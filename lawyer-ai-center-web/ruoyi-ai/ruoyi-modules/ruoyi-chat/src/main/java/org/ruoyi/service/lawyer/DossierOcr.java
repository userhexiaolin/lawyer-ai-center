package org.ruoyi.service.lawyer;
import java.awt.image.BufferedImage;
/** Local OCR only. Cloud/vision providers must not be plugged in without DossierModelGate authorization. */
public interface DossierOcr {
    String recognize(BufferedImage page);
}
