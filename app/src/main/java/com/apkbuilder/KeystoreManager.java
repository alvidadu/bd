package com.apkbuilder;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.math.BigInteger;
import java.security.*;
import java.security.cert.X509Certificate;
import java.util.Date;

/** Creates / loads a real Android debug (or custom) keystore using the standard JDK KeyStore + a self-signed cert. */
public final class KeystoreManager {
    public static final String DEBUG_ALIAS = "androiddebugkey";
    public static final String DEBUG_PASS = "android";

    private KeystoreManager() {}

    public static File ensureDebugKeystore(File dir) throws BuildException {
        File ks = new File(dir, "debug.keystore");
        if (ks.exists()) return ks;
        try {
            createSelfSigned(ks, "PKCS12", DEBUG_ALIAS, DEBUG_PASS, DEBUG_PASS, "CN=Android Debug,O=Android,C=US", 10950);
            return ks;
        } catch (Exception e) {
            throw new BuildException("Signing", "Could not create debug keystore: " + e);
        }
    }

    public static void createSelfSigned(File out, String type, String alias, String storePass, String keyPass,
                                        String dn, int validDays) throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair kp = kpg.generateKeyPair();
        // dn is a full "CN=..." string; SelfSignedCert only needs the CN value.
        String cn = dn.replaceFirst("^CN=", "").split(",")[0];
        X509Certificate cert = SelfSignedCert.generate(kp, cn, validDays);
        KeyStore ks = KeyStore.getInstance(type);
        ks.load(null, null);
        ks.setKeyEntry(alias, kp.getPrivate(), keyPass.toCharArray(), new java.security.cert.Certificate[]{cert});
        try (FileOutputStream fo = new FileOutputStream(out)) {
            ks.store(fo, storePass.toCharArray());
        }
    }

    public static PrivateKey loadKey(File ks, String type, String storePass, String alias, String keyPass) throws Exception {
        KeyStore k = KeyStore.getInstance(type);
        try (FileInputStream in = new FileInputStream(ks)) { k.load(in, storePass.toCharArray()); }
        return (PrivateKey) k.getKey(alias, keyPass.toCharArray());
    }

    public static java.security.cert.X509Certificate loadCert(File ks, String type, String storePass, String alias) throws Exception {
        KeyStore k = KeyStore.getInstance(type);
        try (FileInputStream in = new FileInputStream(ks)) { k.load(in, storePass.toCharArray()); }
        return (java.security.cert.X509Certificate) k.getCertificate(alias);
    }
}
