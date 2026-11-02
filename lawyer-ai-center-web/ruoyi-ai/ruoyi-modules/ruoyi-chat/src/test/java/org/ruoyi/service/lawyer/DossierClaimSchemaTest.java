package org.ruoyi.service.lawyer;

import org.junit.jupiter.api.*;
import org.ruoyi.common.core.exception.ServiceException;
import java.util.*;
import java.awt.image.BufferedImage;
import static org.junit.jupiter.api.Assertions.*;

@Tag("dev")
class DossierClaimSchemaTest {
    static String candidate(){return "{\"claims\":[{\"kind\":\"payment\",\"text\":\"PartyA paid 50000.00\",\"originalText\":\"50000.00\",\"risk\":\"high\",\"values\":{\"dateRaw\":null,\"amountRaw\":\"50000.00\",\"name\":null,\"currency\":\"CNY\"},\"sources\":[{\"anchorId\":\"90071992547409937\",\"relation\":\"supports\"}]}]}";}
    @Test void exactSchemaPreservesLongAnchorAndDecimalStrings(){var item=DossierClaimSchema.parse(candidate()).get(0);assertEquals("90071992547409937",item.sources().get(0).anchorId());assertEquals("50000.00",item.values().get("amountRaw"));assertNull(item.values().get("dateRaw"));}
    @Test void inventedPageStateAndNumericAmountsCannotBypassSchema(){for(String raw:List.of(candidate().replace("\"risk\":\"high\"","\"reviewStatus\":\"confirmed\",\"risk\":\"high\""),candidate().replace("\"anchorId\":","\"pageNo\":99,\"anchorId\":"),candidate().replace("\"50000.00\",\"name\"","50000.00,\"name\""),candidate().replace("\"kind\":\"payment\"","\"kind\":\"payment\",\"kind\":\"date\""),"```json\n"+candidate()+"\n```"))assertThrows(ServiceException.class,()->DossierClaimSchema.parse(raw));}
    @Test void nullOrOversizedOutputsAreExplicitlyRejected(){assertThrows(ServiceException.class,()->DossierClaimSchema.parse(null));assertThrows(ServiceException.class,()->DossierClaimSchema.parse("x".repeat(100001)));assertThrows(ServiceException.class,()->DossierClaimSchema.parse("{\"claims\":{}}"));assertTrue(DossierClaimSchema.parse("{\"claims\":[]}").isEmpty());}
    @Test void promptSeparatesUntrustedEvidenceAndOffersNoTools(){var prompt=DossierClaimSchema.prompt();assertTrue(prompt.contains("untrusted evidence"));assertTrue(prompt.contains("only offered string anchorId"));assertTrue(prompt.contains("no tools"));assertTrue(prompt.contains("contradictory"));}
    @Test void imageRegionsUseNormalizedBoundsAndRealPixels(){var image=new BufferedImage(100,200,BufferedImage.TYPE_INT_RGB);var crop=DossierAnchorService.crop(image,new DossierAnchorService.Region(.2,.1,.3,.5));assertEquals(30,crop.getWidth());assertEquals(100,crop.getHeight());for(var region:List.of(new DossierAnchorService.Region(-.1,0,.2,.2),new DossierAnchorService.Region(0,0,2,1),new DossierAnchorService.Region(0,0,0,1),new DossierAnchorService.Region(Double.NaN,0,.2,.2)))assertThrows(ServiceException.class,()->DossierAnchorService.crop(image,region));}
}
