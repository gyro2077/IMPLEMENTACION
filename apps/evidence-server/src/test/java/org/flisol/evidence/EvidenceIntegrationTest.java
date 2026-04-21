package org.flisol.evidence;

import org.flisol.evidence.web.DashboardController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
public class EvidenceIntegrationTest {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private DashboardController dashboardController;

    @Test
    public void contextLoads() {
        assertNotNull(context, "El contexto de Spring debe subir correctamente");
    }

    @Test
    public void dashboardReturnsHtml() {
        String html = dashboardController.dashboard();
        assertTrue(html.contains("Estado Operativo"), "El dashboard deberia contener el titulo rediseñado");
        assertTrue(html.contains("Inter"), "Deberia utilizar la tipografia moderna");
    }
}
