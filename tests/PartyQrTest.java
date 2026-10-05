package me.jxl.kiosk.plugins.nowplayingoverlay;

import io.nayuki.qrcodegen.QrCode;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;

public final class PartyQrTest {
    public static void main(String[] args) throws Exception {
        for (String link : new String[] {
            "http://192.168.0.18:8095/?join=test-guest-code",
            "https://app.music-assistant.io/?remote_id=demo-server&join=long-guest-code-1234567890",
            "https://ma.test/?join=test-code&name=Stueetagen%20og%20g%C3%A6ster"
        }) {
            QrCode qr = QrCode.encodeText(link, QrCode.Ecc.MEDIUM);
            int scale = 6, size = (qr.size + 8) * scale;
            int[] pixels = new int[size * size];
            for (int y = 0; y < size; y++) for (int x = 0; x < size; x++) {
                pixels[y * size + x] = qr.getModule(x / scale - 4, y / scale - 4) ? 0xFF000000 : 0xFFFFFFFF;
            }
            BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(new RGBLuminanceSource(size, size, pixels)));
            String decoded = new QRCodeReader().decode(bitmap).getText();
            if (!link.equals(decoded)) throw new AssertionError("QR round trip");
        }
        System.out.println("Party QR scan checks passed");
    }
}
