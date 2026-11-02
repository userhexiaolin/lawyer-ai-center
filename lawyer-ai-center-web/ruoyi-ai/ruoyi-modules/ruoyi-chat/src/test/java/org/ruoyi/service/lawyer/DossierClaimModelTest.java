package org.ruoyi.service.lawyer;

import org.junit.jupiter.api.*;
import org.ruoyi.common.chat.domain.vo.chat.ChatModelVo;
import org.ruoyi.common.chat.service.chat.IChatModelService;
import org.ruoyi.common.core.exception.ServiceException;
import org.ruoyi.factory.ChatServiceFactory;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("dev")
class DossierClaimModelTest {
    IChatModelService models;ChatServiceFactory providers;DossierAiProperties properties;DossierClaimModel model;ChatModelVo configured;
    @BeforeEach void setup(){models=mock(IChatModelService.class);providers=mock(ChatServiceFactory.class);properties=new DossierAiProperties();model=new DossierClaimModel(models,providers,properties);configured=new ChatModelVo();configured.setId(7L);configured.setModelName("synthetic-model");configured.setProviderCode("synthetic-provider");configured.setApiHost("https://model.example.test/v1");configured.setApiKey("SYNTHETIC_TEST_SECRET");when(models.queryAvailableList(any())).thenReturn(List.of(configured));}
    @AfterEach void close(){model.close();}
    @Test void unknownRegionAndUnavailableProviderRefuseBeforeConstruction(){assertEquals(503,assertThrows(ServiceException.class,()->model.route("000000",7L)).getCode());properties.getModelRegions().put(7L,"test-region");when(models.queryAvailableList(any())).thenReturn(List.of());assertEquals(503,assertThrows(ServiceException.class,()->model.route("000000",7L)).getCode());verifyNoInteractions(providers);}
    @Test void routeMetadataContainsNoCredentialAndRouteChangeRefusesBeforeConstruction(){properties.getModelRegions().put(7L,"test-region");var route=model.route("000000",7L);assertFalse(route.toString().contains("SYNTHETIC_TEST_SECRET"));configured.setApiHost("https://changed.example.test/v1");assertEquals(409,assertThrows(ServiceException.class,()->model.generate("000000",101L,route,"SYNTHETIC EVIDENCE")).getCode());verifyNoInteractions(providers);}
}
