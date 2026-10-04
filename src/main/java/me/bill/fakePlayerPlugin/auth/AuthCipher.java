package me.bill.fakePlayerPlugin.auth;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.bukkit.plugin.Plugin;

import me.bill.fakePlayerPlugin.util.FppLogger;

/**
 * 加密/解密 {@link BotAuthManager} 存储在数据库中的每个假人密码，使
 * {@code fpp_bot_auth.password_enc} 永远不是明文列，尽管它必须可逆（与登录密码的哈希
 * 不同，这个密码在未来的每次加入时都要再次输入 - 见 {@link BotAuthManager} 自身的类文档）。
 *
 * <p>使用 AES-256/GCM，每次调用随机生成 12 字节 IV，密钥仅保存在内存和磁盘上的一个文件
 * 中：{@code <dataFolder>/auth.key}，首次使用时用 {@link SecureRandom} 生成一次，
 * 此后绝不写入其他任何地方（不进 config.yml，不进日志）。任何能读取该文件加数据库的人
 * 都能恢复所有已存储的密码，与任何其他对称密钥静态加密方案一样 - 请像对待数据库本身
 * 一样备份/限制 {@code auth.key}。
 *
 * <p>丢失或轮换 {@code auth.key} 会使之前存储的所有密码永久无法解密（设计如此 -
 * 加密没有恢复路径）。{@link BotAuthManager} 将解密失败视为“忘记该假人的密码”，
 * 记录一条指向 {@code /fpp auth reset <bot>} 的提示，而不是用垃圾数据静默重试。
 */
final class AuthCipher {

    private static final String KEY_FILE_NAME = "auth.key";
    private static final int KEY_BYTES = 32; // AES-256
    private static final int IV_BYTES = 12; // GCM 推荐的 nonce 长度
    private static final int TAG_BITS = 128;
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    AuthCipher(Plugin plugin) {
        this.key = new SecretKeySpec(loadOrCreateKey(plugin), "AES");
    }

    private byte[] loadOrCreateKey(Plugin plugin) {
        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists()) dataFolder.mkdirs();
        File keyFile = new File(dataFolder, KEY_FILE_NAME);
        try {
            if (keyFile.exists()) {
                byte[] decoded = Base64.getDecoder()
                        .decode(Files.readString(keyFile.toPath()).trim());
                if (decoded.length == KEY_BYTES) return decoded;
                FppLogger.warn("Auth: " + KEY_FILE_NAME + " 格式损坏 - 正在生成新密钥。任何"
                        + "已存储的密码都将无法解密；对之后卡住的任何假人请执行 /fpp auth reset <bot>，"
                        + "以便它在下次加入时注册新密码。");
            }
            byte[] fresh = new byte[KEY_BYTES];
            random.nextBytes(fresh);
            Files.writeString(keyFile.toPath(), Base64.getEncoder().encodeToString(fresh));
            // 尽力而为的文件锁定 - 在不支持 POSIX 风格“仅所有者”权限的文件系统/操作系统上
            // 是无操作（不算失败）（例如普通 Windows/FAT）。
            try {
                keyFile.setReadable(false, false);
                keyFile.setReadable(true, true);
                keyFile.setWritable(false, false);
                keyFile.setWritable(true, true);
            } catch (Throwable ignored) {
            }
            return fresh;
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Auth: 无法在插件数据文件夹中读取或创建 " + KEY_FILE_NAME, e);
        }
    }

    /** Base64(iv || 密文+tag) - 每次调用都使用新的随机 IV，同一密钥可无限期安全复用。 */
    String encrypt(String plaintext) throws GeneralSecurityException {
        byte[] iv = new byte[IV_BYTES];
        random.nextBytes(iv);
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
        byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
        byte[] combined = new byte[iv.length + ciphertext.length];
        System.arraycopy(iv, 0, combined, 0, iv.length);
        System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);
        return Base64.getEncoder().encodeToString(combined);
    }

    String decrypt(String stored) throws GeneralSecurityException {
        byte[] combined = Base64.getDecoder().decode(stored);
        if (combined.length <= IV_BYTES) throw new GeneralSecurityException("存储的值过短");
        byte[] iv = Arrays.copyOfRange(combined, 0, IV_BYTES);
        byte[] ciphertext = Arrays.copyOfRange(combined, IV_BYTES, combined.length);
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
        return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
    }
}