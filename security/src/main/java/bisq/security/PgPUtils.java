/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.security;

import bisq.common.util.ExceptionUtil;
import bisq.common.util.FileUtils;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.openpgp.*;
import org.bouncycastle.openpgp.operator.bc.BcPGPContentVerifierBuilderProvider;
import org.bouncycastle.openpgp.operator.jcajce.JcaKeyFingerprintCalculator;

import java.io.*;
import java.nio.file.Path;
import java.security.SignatureException;
import java.util.Iterator;
import java.util.List;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public class PgPUtils {
    private static final String EXTENSION = ".asc";
    private static final String SIGNING_KEY_FILE = "signingkey.asc";
    private static final String SIGNATURE_FILE = "desktop.jar.asc";
    private static final String KEY_4A133008 = "4A133008";
    private static final String KEY_E222AA02 = "E222AA02";

    public static void verifyDownloadedFile(String directory, String fileName) throws IOException {
        verifyDownloadedFile(directory, fileName, SIGNING_KEY_FILE, SIGNATURE_FILE, List.of(KEY_4A133008, KEY_E222AA02));
    }

    public static void verifyDownloadedFile(String directory, String fileName, String signingKeyFileName, String signatureFileName, List<String> keys) throws IOException {
        for (String key : keys) {
            checkIfKeyMatchesResourceKey(directory, key + EXTENSION);
        }

        String signingKey = FileUtils.readStringFromFile(Path.of(directory, signingKeyFileName).toFile());
        log.debug("signingKey {}", signingKey);
        checkArgument(keys.contains(signingKey), "signingKey not matching any of the provided keys");

        File pubKeyFile = Path.of(directory, signingKey + EXTENSION).toFile();
        File sigFile = Path.of(directory, signatureFileName).toFile();
        File file = Path.of(directory, fileName).toFile();
        log.debug("pubKeyFile {}", pubKeyFile);
        log.debug("sigFile {}", sigFile);
        log.debug("file {}", file);
        checkArgument(PgPUtils.isPgPSignatureValid(pubKeyFile, sigFile, file), "Signature verification failed");
        log.info("signature verification succeeded");
    }

    private static void checkIfKeyMatchesResourceKey(String directory, String keyName) throws IOException {
        String key_4A133008FromResources = FileUtils.readStringFromResource(keyName);
        String key_4A133008_fromDirectory = FileUtils.readStringFromFile(Path.of(directory, keyName).toFile());
        checkArgument(key_4A133008FromResources.equals(key_4A133008_fromDirectory), "Key from directory not matching the one from resources. keyName=" + keyName);
    }

    public static boolean isPgPSignatureValid(File pubKeyFile, File sigFile, File jarFileName) {
        try {
            PGPPublicKeyRing pgpPublicKeyRing = readPgpPublicKeyRing(pubKeyFile);
            PGPSignature pgpSignature = readPgpSignature(sigFile);
            long keyIdFromSignature = pgpSignature.getKeyID();
            log.debug("KeyID used in signature: {}", Integer.toHexString((int) keyIdFromSignature).toUpperCase());
            PGPPublicKey publicKey = checkNotNull(pgpPublicKeyRing.getPublicKey(keyIdFromSignature), "No public key found for key ID from signature");
            long keyIdFromPubKey = publicKey.getKeyID();
            log.debug("The ID of the selected key is {}", Integer.toHexString((int) keyIdFromPubKey).toUpperCase());
            checkArgument(keyIdFromSignature == keyIdFromPubKey, "Key ID from signature not matching key ID from pub Key");
            return isSignatureValid(pgpSignature, publicKey, jarFileName);
        } catch (PGPException | IOException | SignatureException e) {
            log.error("Signature verification failed. \npubKeyFile={} \nsigFile={} \njarFileName={}.\nError: {}",
                    pubKeyFile, sigFile, jarFileName, ExceptionUtil.print(e));
            return false;
        }
    }

    public static PGPPublicKeyRing readPgpPublicKeyRing(File pubKeyFile) throws IOException, PGPException {
        try (InputStream inputStream = PGPUtil.getDecoderStream(new FileInputStream(pubKeyFile))) {
            PGPPublicKeyRingCollection publicKeyRingCollection = new PGPPublicKeyRingCollection(inputStream, new JcaKeyFingerprintCalculator());
            Iterator<PGPPublicKeyRing> iterator = publicKeyRingCollection.getKeyRings();
            if (iterator.hasNext()) {
                return iterator.next();
            } else {
                throw new PGPException("Could not find public keyring in provided key file");
            }
        }
    }

    public static PGPSignature readPgpSignature(File sigFile) throws IOException, SignatureException {
        try (InputStream inputStream = PGPUtil.getDecoderStream(new FileInputStream(sigFile))) {
            PGPObjectFactory pgpObjectFactory = new PGPObjectFactory(inputStream, new JcaKeyFingerprintCalculator());
            Object signatureObject = pgpObjectFactory.nextObject();
            if (signatureObject instanceof PGPSignatureList) {
                PGPSignatureList signatureList = (PGPSignatureList) signatureObject;
                checkArgument(!signatureList.isEmpty(), "signatureList must not be empty");
                return signatureList.get(0);
            } else if (signatureObject instanceof PGPSignature) {
                return (PGPSignature) signatureObject;
            } else {
                throw new SignatureException("Could not find signature in provided signature file");
            }
        }
    }

    public static boolean isSignatureValid(PGPSignature pgpSignature, PGPPublicKey publicKey, File dataFile) throws IOException, PGPException {
        pgpSignature.init(new BcPGPContentVerifierBuilderProvider(), publicKey);
        try (InputStream inputStream = new DataInputStream(new BufferedInputStream(new FileInputStream(dataFile)))) {
            byte[] buffer = new byte[1024];
            int bytesRead;
            while (true) {
                bytesRead = inputStream.read(buffer, 0, 1024);
                if (bytesRead == -1)
                    break;
                pgpSignature.update(buffer, 0, bytesRead);
            }
            return pgpSignature.verify();
        }
    }
}