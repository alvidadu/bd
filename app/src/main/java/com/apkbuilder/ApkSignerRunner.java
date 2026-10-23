package com.apkbuilder;

import com.android.apksig.ApkSigner;
import com.android.apksig.ApkVerifier;

import java.io.File;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Collections;

/** Signs / verifies APKs with the real apksig library (v1 + v2 + v3), in-process. */
public final class ApkSignerRunner {
    private ApkSignerRunner() {}

    public static void sign(File inApk, File outApk, File keystore, String ksType, String storePass,
                            String alias, String keyPass, int minSdk, BuildLogger log) throws BuildException {
        try {
            PrivateKey key = KeystoreManager.loadKey(keystore, ksType, storePass, alias, keyPass);
            X509Certificate cert = KeystoreManager.loadCert(keystore, ksType, storePass, alias);
            if (key == null || cert == null) throw new BuildException("Signing", "Alias '" + alias + "' not found in keystore");
            ApkSigner.SignerConfig signerConfig = new ApkSigner.SignerConfig.Builder(
                    "builder-key", key, Collections.singletonList(cert)).build();
            ApkSigner signer = new ApkSigner.Builder(Collections.singletonList(signerConfig))
                    .setInputApk(inApk)
                    .setOutputApk(outApk)
                    .setMinSdkVersion(minSdk)
                    .setV1SigningEnabled(true)
                    .setV2SigningEnabled(true)
                    .setV3SigningEnabled(true)
                    .setCreatedBy("APK Builder")
                    .build();
            signer.sign();
            log.log("APK Signer", "Signed with v1+v2+v3: " + outApk.getName());
        } catch (BuildException e) {
            throw e;
        } catch (Exception e) {
            throw new BuildException("Signing", "Signing failed: " + e);
        }
    }

    public static boolean verify(File apk, int minSdk, BuildLogger log) {
        try {
            ApkVerifier.Result result = new ApkVerifier.Builder(apk).setMinCheckedPlatformVersion(minSdk).build().verify();
            if (result.isVerified()) {
                log.log("APK Signer", "Verification OK (v1=" + result.isVerifiedUsingV1Scheme()
                        + " v2=" + result.isVerifiedUsingV2Scheme() + " v3=" + result.isVerifiedUsingV3Scheme() + ")");
                return true;
            }
            for (ApkVerifier.IssueWithParams e : result.getErrors()) log.log("APK Signer", "Verify error: " + e);
            return false;
        } catch (Exception e) {
            log.log("APK Signer", "Verification threw: " + e);
            return false;
        }
    }
}
