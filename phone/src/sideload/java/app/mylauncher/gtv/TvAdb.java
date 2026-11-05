package app.mylauncher.gtv;

import android.content.Context;
import android.os.Build;
import android.sun.misc.BASE64Encoder;
import android.sun.security.provider.X509Factory;
import android.sun.security.x509.AlgorithmId;
import android.sun.security.x509.CertificateAlgorithmId;
import android.sun.security.x509.CertificateExtensions;
import android.sun.security.x509.CertificateIssuerName;
import android.sun.security.x509.CertificateSerialNumber;
import android.sun.security.x509.CertificateSubjectName;
import android.sun.security.x509.CertificateValidity;
import android.sun.security.x509.CertificateVersion;
import android.sun.security.x509.CertificateX509Key;
import android.sun.security.x509.KeyIdentifier;
import android.sun.security.x509.PrivateKeyUsageExtension;
import android.sun.security.x509.SubjectKeyIdentifierExtension;
import android.sun.security.x509.X500Name;
import android.sun.security.x509.X509CertImpl;
import android.sun.security.x509.X509CertInfo;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Date;
import java.util.Random;

import io.github.muntashirakon.adb.AbsAdbConnectionManager;

/**
 * The phone's identity towards the TV's wireless debugging. The key is made once and kept, so after pairing the
 * TV recognises this phone later without pairing again.
 */
final class TvAdb extends AbsAdbConnectionManager {
    private static TvAdb instance;

    static synchronized TvAdb get(Context c) throws Exception {
        if (instance == null) instance = new TvAdb(c.getApplicationContext());
        return instance;
    }

    private PrivateKey key;
    private Certificate cert;

    private TvAdb(Context c) throws Exception {
        setApi(Build.VERSION.SDK_INT);
        File kf = new File(c.getFilesDir(), "tv_private.key");
        File cf = new File(c.getFilesDir(), "tv_cert.pem");
        if (kf.exists() && cf.exists()) {
            try {
                byte[] b = new byte[(int) kf.length()];
                try (InputStream in = new FileInputStream(kf)) {
                    int off = 0;
                    while (off < b.length) {
                        int n = in.read(b, off, b.length - off);
                        if (n < 0) break;
                        off += n;
                    }
                }
                key = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(b));
                try (InputStream in = new FileInputStream(cf)) {
                    cert = CertificateFactory.getInstance("X.509").generateCertificate(in);
                }
            } catch (Exception e) {
                key = null;
                cert = null;
            }
        }
        if (key == null || cert == null) {
            KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
            g.initialize(2048, SecureRandom.getInstance("SHA1PRNG"));
            KeyPair kp = g.generateKeyPair();
            PublicKey pub = kp.getPublic();
            key = kp.getPrivate();
            String algo = "SHA512withRSA";
            Date from = new Date();
            Date to = new Date(System.currentTimeMillis() + 3650L * 86400000L);
            CertificateExtensions ext = new CertificateExtensions();
            ext.set("SubjectKeyIdentifier", new SubjectKeyIdentifierExtension(new KeyIdentifier(pub).getIdentifier()));
            X500Name name = new X500Name("CN=Salah Khair");
            ext.set("PrivateKeyUsage", new PrivateKeyUsageExtension(from, to));
            X509CertInfo info = new X509CertInfo();
            info.set("version", new CertificateVersion(2));
            info.set("serialNumber", new CertificateSerialNumber(new Random().nextInt() & Integer.MAX_VALUE));
            info.set("algorithmID", new CertificateAlgorithmId(AlgorithmId.get(algo)));
            info.set("subject", new CertificateSubjectName(name));
            info.set("key", new CertificateX509Key(pub));
            info.set("validity", new CertificateValidity(from, to));
            info.set("issuer", new CertificateIssuerName(name));
            info.set("extensions", ext);
            X509CertImpl impl = new X509CertImpl(info);
            impl.sign(key, algo);
            cert = impl;
            try (OutputStream os = new FileOutputStream(kf)) {
                os.write(key.getEncoded());
            }
            try (OutputStream os = new FileOutputStream(cf)) {
                os.write(X509Factory.BEGIN_CERT.getBytes(StandardCharsets.UTF_8));
                os.write('\n');
                new BASE64Encoder().encode(cert.getEncoded(), os);
                os.write('\n');
                os.write(X509Factory.END_CERT.getBytes(StandardCharsets.UTF_8));
            }
        }
    }

    @Override
    protected PrivateKey getPrivateKey() {
        return key;
    }

    @Override
    protected Certificate getCertificate() {
        return cert;
    }

    @Override
    protected String getDeviceName() {
        return "Salah Khair";
    }
}
