package com.apkbuilder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Date;

/**
 * Minimal hand-rolled DER/ASN.1 X.509v3 self-signed certificate generator.
 * No third-party crypto library needed (Bouncy Castle etc. cannot be fetched by this offline
 * builder): this writes the certificate bytes by hand and signs them with the standard
 * java.security API, then hands the DER bytes to the platform's own CertificateFactory - the
 * same class Android uses to parse every certificate it already trusts.
 */
public final class SelfSignedCert {
    private SelfSignedCert() {}

    public static X509Certificate generate(KeyPair kp, String cn, int validDays) throws Exception {
        long now = System.currentTimeMillis();
        Date notBefore = new Date(now - 24L * 3600 * 1000);
        Date notAfter = new Date(now + validDays * 24L * 3600 * 1000);
        byte[] serial = new byte[8];
        new java.security.SecureRandom().nextBytes(serial);
        serial[0] = (byte) (serial[0] & 0x7F);

        byte[] tbs = tbsCertificate(kp.getPublic(), cn, serial, notBefore, notAfter);
        Signature sig = Signature.getInstance("SHA256withRSA");
        sig.initSign(kp.getPrivate());
        sig.update(tbs);
        byte[] signature = sig.sign();

        byte[] algId = seq(cat(oid("1.2.840.113549.1.1.11"), NULL));
        byte[] certSeq = seq(cat(tbs, algId, bitString(signature)));

        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        return (X509Certificate) cf.generateCertificate(new java.io.ByteArrayInputStream(certSeq));
    }

    private static byte[] tbsCertificate(PublicKey pub, String cn, byte[] serial, Date nb, Date na) throws IOException {
        byte[] version = ctx(0, integer(new byte[]{2})); // v3
        byte[] serialTlv = integer(serial);
        byte[] sigAlg = seq(cat(oid("1.2.840.113549.1.1.11"), NULL));
        byte[] issuer = name(cn);
        byte[] validity = seq(cat(utcOrGeneralized(nb), utcOrGeneralized(na)));
        byte[] subject = issuer;
        byte[] spki = pub.getEncoded(); // already a DER SubjectPublicKeyInfo for RSA keys
        return seq(cat(version, serialTlv, sigAlg, issuer, validity, subject, spki));
    }

    private static byte[] name(String cn) throws IOException {
        byte[] cnOid = oid("2.5.4.3");
        byte[] cnVal = tag(0x0C, cn.getBytes("UTF-8")); // UTF8String
        byte[] atv = seq(cat(cnOid, cnVal));
        byte[] rdn = tag(0x31, atv); // SET
        return tag(0x30, rdn); // SEQUENCE (Name)
    }

    private static byte[] utcOrGeneralized(Date d) {
        java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("yyMMddHHmmss'Z'");
        f.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
        try {
            return tag(0x17, f.format(d).getBytes("ASCII")); // UTCTime
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static final byte[] NULL = new byte[]{0x05, 0x00};

    private static byte[] integer(byte[] v) {
        return tag(0x02, v);
    }

    private static byte[] bitString(byte[] v) {
        byte[] withPad = new byte[v.length + 1];
        System.arraycopy(v, 0, withPad, 1, v.length);
        return tag(0x03, withPad);
    }

    private static byte[] seq(byte[] content) { return tag(0x30, content); }

    private static byte[] ctx(int n, byte[] content) { return tag(0xA0 | n, content); }

    private static byte[] oid(String dotted) {
        String[] parts = dotted.split("\\.");
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        int first = Integer.parseInt(parts[0]) * 40 + Integer.parseInt(parts[1]);
        b.write(first);
        for (int i = 2; i < parts.length; i++) {
            long val = Long.parseLong(parts[i]);
            byte[] chunk = base128(val);
            b.write(chunk, 0, chunk.length);
        }
        return tag(0x06, b.toByteArray());
    }

    private static byte[] base128(long val) {
        if (val == 0) return new byte[]{0};
        java.util.List<Byte> bytes = new java.util.ArrayList<>();
        while (val > 0) {
            bytes.add(0, (byte) (val & 0x7F));
            val >>= 7;
        }
        for (int i = 0; i < bytes.size() - 1; i++) bytes.set(i, (byte) (bytes.get(i) | 0x80));
        byte[] out = new byte[bytes.size()];
        for (int i = 0; i < out.length; i++) out[i] = bytes.get(i);
        return out;
    }

    private static byte[] tag(int tagByte, byte[] content) {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        b.write(tagByte);
        writeLength(b, content.length);
        b.write(content, 0, content.length);
        return b.toByteArray();
    }

    private static void writeLength(ByteArrayOutputStream b, int len) {
        if (len < 0x80) {
            b.write(len);
        } else {
            byte[] be = java.math.BigInteger.valueOf(len).toByteArray();
            int off = (be.length > 1 && be[0] == 0) ? 1 : 0;
            b.write(0x80 | (be.length - off));
            b.write(be, off, be.length - off);
        }
    }

    private static byte[] cat(byte[]... parts) {
        int len = 0;
        for (byte[] p : parts) len += p.length;
        byte[] out = new byte[len];
        int pos = 0;
        for (byte[] p : parts) { System.arraycopy(p, 0, out, pos, p.length); pos += p.length; }
        return out;
    }
}
