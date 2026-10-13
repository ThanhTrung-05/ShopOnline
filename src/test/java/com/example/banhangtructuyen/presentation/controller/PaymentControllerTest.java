package com.example.banhangtructuyen.presentation.controller;

import com.example.banhangtructuyen.application.dto.order.DemoConfirmRequest;
import com.example.banhangtructuyen.application.dto.order.PaymentStatusResponse;
import com.example.banhangtructuyen.application.dto.order.QrPaymentResponse;
import com.example.banhangtructuyen.application.service.AuthenticatedCustomerResolver;
import com.example.banhangtructuyen.application.service.PaymentService;
import com.example.banhangtructuyen.application.service.QrPaymentService;
import com.example.banhangtructuyen.domain.exception.ResourceNotFoundException;
import com.example.banhangtructuyen.domain.model.Customer;
import com.example.banhangtructuyen.domain.model.Payment;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.time.Instant;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("PaymentController MockMvc Tests — ATS-27 & ATS-28")
class PaymentControllerTest {

    private static final String CUSTOMER_SUBJECT = "cust-sub-123";
    private static final Long CUSTOMER_ID = 100L;
    private static final Long ORDER_ID = 50L;
    private static final String ORDER_NUMBER = "ORD-20261013-ABC";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PaymentService paymentService;

    @MockBean
    private QrPaymentService qrPaymentService;

    @MockBean
    private AuthenticatedCustomerResolver authenticatedCustomerResolver;

    private RequestPostProcessor customerJwt() {
        return jwt().jwt(builder -> builder.subject(CUSTOMER_SUBJECT))
                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"));
    }

    private RequestPostProcessor adminJwt() {
        return jwt().jwt(builder -> builder.subject("admin-sub"))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    @BeforeEach
    void setUp() {
        when(authenticatedCustomerResolver.resolveActiveCustomer(CUSTOMER_SUBJECT))
                .thenReturn(Customer.builder().customerId(CUSTOMER_ID).build());
    }

    // =========================================================================
    // ATS-27: GET /api/v1/payments/qr/{orderId}
    // =========================================================================

    @Nested
    @DisplayName("ATS-27: GET /api/v1/payments/qr/{orderId}")
    class GenerateQrTests {

        @Test
        @DisplayName("200 - CUSTOMER receives QR payment response")
        void generateQr_success() throws Exception {
            final QrPaymentResponse response = new QrPaymentResponse(
                    99L,
                    ORDER_ID,
                    ORDER_NUMBER,
                    BigDecimal.valueOf(250000),
                    "50|ORD-20261013-ABC|250000.00",
                    Payment.PaymentStatus.PROCESSING,
                    Instant.now().plusSeconds(900)
            );
            when(qrPaymentService.generateQr(ORDER_ID, CUSTOMER_ID)).thenReturn(response);

            mockMvc.perform(get("/api/v1/payments/qr/{orderId}", ORDER_ID)
                            .with(customerJwt())
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.paymentId").value(99))
                    .andExpect(jsonPath("$.data.orderId").value(50))
                    .andExpect(jsonPath("$.data.orderNumber").value(ORDER_NUMBER))
                    .andExpect(jsonPath("$.data.amount").value(250000))
                    .andExpect(jsonPath("$.data.qrCode").value("50|ORD-20261013-ABC|250000.00"))
                    .andExpect(jsonPath("$.data.status").value("PROCESSING"));

            verify(qrPaymentService).generateQr(ORDER_ID, CUSTOMER_ID);
        }

        @Test
        @DisplayName("401 - Unauthenticated request is rejected")
        void generateQr_unauthenticated() throws Exception {
            mockMvc.perform(get("/api/v1/payments/qr/{orderId}", ORDER_ID))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(qrPaymentService);
        }

        @Test
        @DisplayName("403 - ADMIN cannot call customer QR endpoint")
        void generateQr_forbiddenForAdmin() throws Exception {
            mockMvc.perform(get("/api/v1/payments/qr/{orderId}", ORDER_ID)
                            .with(adminJwt()))
                    .andExpect(status().isForbidden());

            verifyNoInteractions(qrPaymentService);
        }

        @Test
        @DisplayName("404 - Order not found throws ResourceNotFoundException")
        void generateQr_notFound() throws Exception {
            when(qrPaymentService.generateQr(ORDER_ID, CUSTOMER_ID))
                    .thenThrow(new ResourceNotFoundException("Order", ORDER_ID.toString()));

            mockMvc.perform(get("/api/v1/payments/qr/{orderId}", ORDER_ID)
                            .with(customerJwt()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("Order not found with id: 50"));
        }

        @Test
        @DisplayName("400 - Order not in PENDING status returns 400")
        void generateQr_notPending() throws Exception {
            when(qrPaymentService.generateQr(ORDER_ID, CUSTOMER_ID))
                    .thenThrow(new IllegalArgumentException("Đơn hàng không ở trạng thái chờ thanh toán"));

            mockMvc.perform(get("/api/v1/payments/qr/{orderId}", ORDER_ID)
                            .with(customerJwt()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("Đơn hàng không ở trạng thái chờ thanh toán"));
        }
    }

    // =========================================================================
    // ATS-27: GET /api/v1/payments/{orderId}/status
    // =========================================================================

    @Nested
    @DisplayName("ATS-27: GET /api/v1/payments/{orderId}/status")
    class GetPaymentStatusTests {

        @Test
        @DisplayName("200 - CUSTOMER gets payment status")
        void getPaymentStatus_success() throws Exception {
            final PaymentStatusResponse response = new PaymentStatusResponse(
                    ORDER_ID,
                    Payment.PaymentStatus.PROCESSING
            );
            when(qrPaymentService.getPaymentStatus(ORDER_ID, CUSTOMER_ID)).thenReturn(response);

            mockMvc.perform(get("/api/v1/payments/{orderId}/status", ORDER_ID)
                            .with(customerJwt()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.orderId").value(50))
                    .andExpect(jsonPath("$.data.status").value("PROCESSING"));

            verify(qrPaymentService).getPaymentStatus(ORDER_ID, CUSTOMER_ID);
        }

        @Test
        @DisplayName("401 - Unauthenticated status request rejected")
        void getPaymentStatus_unauthenticated() throws Exception {
            mockMvc.perform(get("/api/v1/payments/{orderId}/status", ORDER_ID))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(qrPaymentService);
        }

        @Test
        @DisplayName("404 - Order not found returns 404")
        void getPaymentStatus_notFound() throws Exception {
            when(qrPaymentService.getPaymentStatus(ORDER_ID, CUSTOMER_ID))
                    .thenThrow(new ResourceNotFoundException("Order", ORDER_ID.toString()));

            mockMvc.perform(get("/api/v1/payments/{orderId}/status", ORDER_ID)
                            .with(customerJwt()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false));
        }
    }

    // =========================================================================
    // ATS-28: POST /api/v1/payments/demo/confirm
    // =========================================================================

    @Nested
    @DisplayName("ATS-28: POST /api/v1/payments/demo/confirm")
    class DemoConfirmTests {

        @Test
        @DisplayName("200 - ADMIN successfully confirms demo payment")
        void demoConfirm_success() throws Exception {
            final DemoConfirmRequest request = new DemoConfirmRequest(
                    ORDER_NUMBER,
                    BigDecimal.valueOf(250000)
            );
            doNothing().when(qrPaymentService).demoConfirm(request);

            mockMvc.perform(post("/api/v1/payments/demo/confirm")
                            .with(adminJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            verify(qrPaymentService).demoConfirm(request);
        }

        @Test
        @DisplayName("403 - CUSTOMER is forbidden from calling demo confirm")
        void demoConfirm_forbiddenForCustomer() throws Exception {
            final DemoConfirmRequest request = new DemoConfirmRequest(
                    ORDER_NUMBER,
                    BigDecimal.valueOf(250000)
            );

            mockMvc.perform(post("/api/v1/payments/demo/confirm")
                            .with(customerJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());

            verifyNoInteractions(qrPaymentService);
        }

        @Test
        @DisplayName("401 - Unauthenticated demo confirm rejected")
        void demoConfirm_unauthenticated() throws Exception {
            final DemoConfirmRequest request = new DemoConfirmRequest(
                    ORDER_NUMBER,
                    BigDecimal.valueOf(250000)
            );

            mockMvc.perform(post("/api/v1/payments/demo/confirm")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(qrPaymentService);
        }

        @Test
        @DisplayName("400 - Invalid request body (missing orderNumber or negative amount)")
        void demoConfirm_validationError() throws Exception {
            final DemoConfirmRequest invalidRequest = new DemoConfirmRequest(
                    "",
                    BigDecimal.valueOf(-100)
            );

            mockMvc.perform(post("/api/v1/payments/demo/confirm")
                            .with(adminJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(invalidRequest)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false));

            verifyNoInteractions(qrPaymentService);
        }

        @Test
        @DisplayName("403 - Service throws UnsupportedOperationException when demo mode is disabled")
        void demoConfirm_demoModeDisabled() throws Exception {
            final DemoConfirmRequest request = new DemoConfirmRequest(
                    ORDER_NUMBER,
                    BigDecimal.valueOf(250000)
            );
            doThrow(new UnsupportedOperationException("Demo payment confirmation is disabled in this environment"))
                    .when(qrPaymentService).demoConfirm(request);

            mockMvc.perform(post("/api/v1/payments/demo/confirm")
                            .with(adminJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("Demo payment confirmation is disabled in this environment"));
        }
    }
}
