package org.ruoyi.controller.chat;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.ruoyi.common.chat.domain.vo.chat.ChatModelVo;
import org.ruoyi.common.chat.service.chat.IChatModelService;
import org.ruoyi.common.core.exception.ServiceException;
import org.ruoyi.domain.vo.agent.AgentVo;
import org.ruoyi.factory.ChatServiceFactory;
import org.ruoyi.service.agent.IAgentService;
import org.ruoyi.service.chat.AbstractChatService;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("dev")
class ContractReviewControllerTest {
    @Test void draftingAcceptsUpTo200MbWithoutTheReviewTenMbLimit() throws Exception {
        var output = new ByteArrayOutputStream();
        try (var document = new XWPFDocument()) {
            document.createParagraph().createRun().setText("合同约定价款10000元。");
            document.write(output);
        }
        var controller = new ContractReviewController(null, null, null);
        var atLimit = new MockMultipartFile("file", "contract.docx", "application/octet-stream", output.toByteArray()) {
            @Override public long getSize() { return 200L * 1024 * 1024; }
        };
        assertEquals("合同约定价款10000元。", controller.draftingContent(atLimit).getData().text());
        assertThrows(ServiceException.class, () -> controller.content(atLimit));
        var aboveLimit = new MockMultipartFile("file", "contract.docx", "application/octet-stream", output.toByteArray()) {
            @Override public long getSize() { return 200L * 1024 * 1024 + 1; }
        };
        assertTrue(assertThrows(ServiceException.class, () -> controller.draftingContent(aboveLimit)).getMessage().contains("200MB"));
    }
    @Test void readsActualWordParagraphs() throws Exception {
        var output = new ByteArrayOutputStream();
        try (var document = new XWPFDocument()) {
            document.createParagraph().createRun().setText("月租金3000元，租期一年。");
            document.write(output);
        }
        var controller = new ContractReviewController(null, null, null);
        var result = controller.content(new MockMultipartFile("file", "contract.docx", "application/octet-stream", output.toByteArray()));
        assertEquals("月租金3000元，租期一年。", result.getData().text());
    }

    @Test void readsActualPdfText() throws Exception {
        var output = new ByteArrayOutputStream();
        try (var pdf = new PDDocument()) {
            var page = new PDPage(); pdf.addPage(page);
            try (var content = new PDPageContentStream(pdf, page)) {
                content.beginText(); content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(40, 700); content.showText("Rent: 3000 CNY per month"); content.endText();
            }
            pdf.save(output);
        }
        var controller = new ContractReviewController(null, null, null);
        var result = controller.content(new MockMultipartFile("file", "contract.pdf", "application/pdf", output.toByteArray()));
        assertTrue(result.getData().text().contains("3000 CNY"));
    }

    @Test void rejectsEmptyUnsupportedAndCorruptDocuments() {
        var controller = new ContractReviewController(null, null, null);
        for (String name : List.of("contract.exe", "broken.docx", "broken.pdf", "broken.png")) {
            assertThrows(ServiceException.class, () -> controller.content(new MockMultipartFile("file", name, "application/octet-stream", new byte[]{1, 2, 3})));
        }
        assertThrows(ServiceException.class, () -> controller.content(new MockMultipartFile("file", "empty.pdf", "application/pdf", new byte[0])));
    }

    @Test @SuppressWarnings("unchecked") void imageOcrReceivesActualImageBytesNotAUrl() throws Exception {
        var agents = mock(IAgentService.class); var models = mock(IChatModelService.class);
        var factory = mock(ChatServiceFactory.class); var provider = mock(AbstractChatService.class); var chat = mock(ChatModel.class);
        var agent = new AgentVo(); agent.setAgentName("合同审查官"); agent.setModelId(9L);
        var model = new ChatModelVo(); model.setProviderCode("vllm");
        when(agents.queryEnabledOptions()).thenReturn(List.of(agent)); when(models.queryById(9L)).thenReturn(model);
        when(factory.getOriginalService("vllm")).thenReturn(provider); when(provider.buildChatModel(model)).thenReturn(chat);
        when(chat.chat(anyList())).thenReturn(ChatResponse.builder().aiMessage(AiMessage.from("识别出的合同正文")).build());
        var output = new ByteArrayOutputStream(); ImageIO.write(new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB), "png", output);
        var result = new ContractReviewController(agents, models, factory).content(new MockMultipartFile("file", "contract.png", "image/png", output.toByteArray()));
        assertEquals("识别出的合同正文", result.getData().text());
        var messages = ArgumentCaptor.forClass(List.class); verify(chat).chat(messages.capture());
        List<ChatMessage> sent = messages.getValue(); assertInstanceOf(dev.langchain4j.data.message.ImageContent.class, assertInstanceOf(UserMessage.class, sent.get(1)).contents().get(0));
    }
}
