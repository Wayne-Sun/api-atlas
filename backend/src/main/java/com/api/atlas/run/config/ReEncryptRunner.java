package com.api.atlas.run.config;

import com.api.atlas.config.EncryptionUtil;
import com.api.atlas.mapper.DataSourceMapper;
import com.api.atlas.model.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;
import java.util.List;

/**
 * One-shot, profile-gated runner that re-encrypts every non-blank
 * {@code data_source.password} row from the OLD AES key (backup of the leaked
 * key, configured via {@code atlas.encryption.old-secret-key}) to the CURRENT
 * {@link SecretKey} bean ({@code atlas.encryption.secret-key}).
 *
 * <p>Fail-closed: if any row cannot be decrypted with the old key, the whole
 * transaction is rolled back and an {@link IllegalStateException} is thrown —
 * no row is ever left stranded encrypted with the old key. Idempotent: rows
 * already encrypted with the current key are detected via a new-key decrypt
 * probe and skipped, so the runner can be safely re-run.
 *
 * <p>Activated only with the {@code rotate} profile, so normal/test runs never
 * touch the data.
 */
@Component
@Profile("rotate")
public class ReEncryptRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(ReEncryptRunner.class);

    private static final String PLACEHOLDER_PREFIX = "CHANGE_ME_";

    private final DataSourceMapper dataSourceMapper;
    private final SecretKey secretKey;
    private final String oldSecretKey;

    public ReEncryptRunner(DataSourceMapper dataSourceMapper,
                           SecretKey secretKey,
                           @Value("${atlas.encryption.old-secret-key:}") String oldSecretKey) {
        this.dataSourceMapper = dataSourceMapper;
        this.secretKey = secretKey;
        this.oldSecretKey = oldSecretKey;
    }

    @Override
    @Transactional
    public void run(String... args) {
        SecretKey oldKey = buildOldKey();
        List<DataSource> rows = dataSourceMapper.selectAll();

        int reEncrypted = 0;
        int failed = 0;
        for (DataSource row : rows) {
            String password = row.getPassword();
            if (password == null || password.isBlank()) {
                continue;
            }
            if (canDecrypt(password, secretKey)) {
                // Already encrypted with the current (new) key — idempotent re-run.
                continue;
            }
            try {
                String plaintext = EncryptionUtil.decrypt(password, oldKey);
                row.setPassword(EncryptionUtil.encrypt(plaintext, secretKey));
                dataSourceMapper.updateById(row);
                reEncrypted++;
            } catch (RuntimeException e) {
                failed++;
                log.error("Row id={} could not be verified with the old key: {}", row.getId(), e.getMessage());
            }
        }

        if (failed > 0) {
            throw new IllegalStateException(
                    failed + " rows could not be decrypted with the old key — resolve before rotating");
        }
        log.info("Re-encrypted {} rows", reEncrypted);
    }

    private SecretKey buildOldKey() {
        if (oldSecretKey == null || oldSecretKey.isBlank() || oldSecretKey.startsWith(PLACEHOLDER_PREFIX)) {
            throw new IllegalStateException("atlas.encryption.old-secret-key must be set for rotate profile");
        }
        return new SecretKeySpec(Base64.getDecoder().decode(oldSecretKey), "AES");
    }

    private boolean canDecrypt(String ciphertext, SecretKey key) {
        try {
            EncryptionUtil.decrypt(ciphertext, key);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }
}
