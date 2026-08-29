package app.revanced.bilibili.utils;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.Signature;
import android.os.Process;

import androidx.annotation.Keep;

import java.security.MessageDigest;

@Keep
public final class IntegrityVerifier {
    public static final String EXPECTED_SIGNATURE_SHA256 = "FFDDEA21507BE262D0A7C802D8947E34466B076A06634846CC5EE4BC09ED01F1";
    private static volatile boolean verified = false;

    private IntegrityVerifier() {}

    @Keep
    public static void verifySignatureBytes(byte[] signatureBytes) {
        if (signatureBytes == null || signatureBytes.length == 0) {
            triggerProtection("Empty signature");
            return;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(signatureBytes);
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02X", b));
            }
            String actualSha256 = sb.toString();
            if (!EXPECTED_SIGNATURE_SHA256.equalsIgnoreCase(actualSha256)) {
                triggerProtection("Signature SHA256 mismatch: " + actualSha256);
            } else {
                verified = true;
            }
        } catch (Throwable t) {
            triggerProtection("Signature verify failed: " + t.getMessage());
        }
    }

    @Keep
    public static void verifySignature(Signature signature) {
        if (signature == null) {
            triggerProtection("Null signature");
            return;
        }
        verifySignatureBytes(signature.toByteArray());
    }

    @SuppressLint("PackageManagerGetSignatures")
    @Keep
    public static void verifyContext(Context context) {
        if (context == null) return;
        try {
            // Check Package Name
            String packageName = context.getPackageName();
            if (!"tv.danmaku.bili".equals(packageName)) {
                triggerProtection("Package name mismatch: " + packageName);
                return;
            }

            // Check Debuggable
            ApplicationInfo appInfo = context.getApplicationInfo();
            if (appInfo != null && (appInfo.flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
                triggerProtection("Debuggable flag detected");
                return;
            }

            // Check Native Layer
            try {
                if (!nativeVerify(context)) {
                    triggerProtection("Native integrity check failed");
                    return;
                }
            } catch (UnsatisfiedLinkError ignored) {
                // Ignore if native lib not loaded in secondary process
            }
        } catch (Throwable t) {
            triggerProtection("Integrity verify failed: " + t.getMessage());
        }
    }

    @Keep
    public static boolean isVerified() {
        return verified;
    }

    @Keep
    public static void triggerProtection(String reason) {
        try {
            Logger.error(() -> "SECURITY ALERT: [BRZQ] " + reason);
        } catch (Throwable ignored) {}
        try {
            Process.killProcess(Process.myPid());
        } catch (Throwable ignored) {}
        try {
            System.exit(0);
        } catch (Throwable ignored) {}
    }

    public static native boolean nativeVerify(Context context);
}
