package com.credchain.modules.certificate.document;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("QrCodeGenerator")
class QrCodeGeneratorTest {

    @Test
    @DisplayName("PNG of the requested size that decodes back to the same URL")
    void roundTrip() throws Exception {
        String url = "http://localhost:5173/verify/0x" + "ab".repeat(32);

        byte[] png = QrCodeGenerator.png(url, 300);
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));

        assertThat(image.getWidth()).isEqualTo(300);
        assertThat(image.getHeight()).isEqualTo(300);
        assertThat(decode(image)).isEqualTo(url);
    }

    private static String decode(BufferedImage image) throws Exception {
        int[] pixels = image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
        var source = new RGBLuminanceSource(image.getWidth(), image.getHeight(), pixels);
        return new QRCodeReader().decode(new BinaryBitmap(new HybridBinarizer(source))).getText();
    }
}