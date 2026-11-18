package com.gouyan.web.controller.bill.service.impl;

import com.gouyan.common.exception.DataNotFoundException;
import com.gouyan.system.domin.SysBill;
import com.gouyan.system.domin.SysMovie;
import com.gouyan.system.domin.SysSession;
import com.gouyan.system.domin.vo.SysBillVo;
import com.gouyan.system.mapper.SysBillMapper;
import com.gouyan.system.mapper.SysMovieMapper;
import com.gouyan.system.mapper.SysSessionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BillPaymentServiceImplTest {

    private SysBillMapper billMapper;
    private SysSessionMapper sessionMapper;
    private SysMovieMapper movieMapper;
    private BillPaymentServiceImpl paymentService;

    @BeforeEach
    void setUp() {
        billMapper = mock(SysBillMapper.class);
        sessionMapper = mock(SysSessionMapper.class);
        movieMapper = mock(SysMovieMapper.class);
        paymentService = new BillPaymentServiceImpl(billMapper, sessionMapper, movieMapper);
    }

    @Test
    void paidBillCannotBeChangedBackToUnpaidWithoutRefund() {
        when(billMapper.findByIdForUpdate(1L)).thenReturn(bill(1L, true, 10L, "[\"1排1号\"]"));
        SysBillVo request = paymentRequest(1L, "{}");
        request.getSysBill().setBillState(false);
        assertThrows(DataNotFoundException.class, () -> paymentService.updateBill(request));
        verify(billMapper, never()).update(any(SysBill.class));
    }

    @Test
    void missingSessionDoesNotMarkBillAsPaid() {
        SysBill persisted = bill(1L, false, 99L, "[\"1排1号\"]");
        when(billMapper.findByIdForUpdate(1L)).thenReturn(persisted);
        when(sessionMapper.findOneForUpdate(99L)).thenReturn(null);

        assertThrows(DataNotFoundException.class,
                () -> paymentService.updateBill(paymentRequest(1L, "{}")));

        verify(billMapper, never()).update(any(SysBill.class));
        verify(movieMapper, never()).update(any(SysMovie.class));
    }

    @Test
    void repeatedPaymentIsIdempotent() {
        SysBill persisted = bill(1L, true, 10L, "[\"1排1号\"]");
        when(billMapper.findByIdForUpdate(1L)).thenReturn(persisted);

        assertEquals(1, paymentService.updateBill(paymentRequest(1L, "{}")));

        verify(billMapper, never()).update(any(SysBill.class));
        verify(sessionMapper, never()).update(any(SysSession.class));
        verify(movieMapper, never()).update(any(SysMovie.class));
    }

    @Test
    void successfulPaymentUpdatesBillSessionAndBoxOfficeOnce() {
        SysBill persisted = bill(1L, false, 10L, "[\"1排1号\",\"1排2号\"]");
        SysSession session = new SysSession();
        session.setSessionId(10L);
        session.setMovieId(20L);
        session.setSessionPrice(35.0);
        session.setSessionSeats("{\"1\":[0,0]}");
        SysMovie movie = new SysMovie();
        movie.setMovieId(20L);
        movie.setMovieBoxOffice(100.0);
        when(billMapper.findByIdForUpdate(1L)).thenReturn(persisted);
        when(sessionMapper.findOneForUpdate(10L)).thenReturn(session);
        when(movieMapper.findOne(20L)).thenReturn(movie);
        when(billMapper.update(any(SysBill.class))).thenReturn(1);
        when(sessionMapper.update(any(SysSession.class))).thenReturn(1);
        when(movieMapper.update(any(SysMovie.class))).thenReturn(1);

        assertEquals(1, paymentService.updateBill(paymentRequest(1L, "{\"1\":[3,3]}")));

        ArgumentCaptor<SysMovie> movieCaptor = ArgumentCaptor.forClass(SysMovie.class);
        verify(movieMapper).update(movieCaptor.capture());
        assertEquals(170.0, movieCaptor.getValue().getMovieBoxOffice(), 0.001);
        assertEquals("{\"1\":[3,3]}", session.getSessionSeats());
    }

    @Test
    void soldSeatIsRejectedBeforeAnyUpdate() {
        SysBill persisted = bill(1L, false, 10L, "[\"1排1号\"]");
        SysSession session = new SysSession();
        session.setSessionId(10L);
        session.setMovieId(20L);
        session.setSessionPrice(35.0);
        session.setSessionSeats("{\"1\":[3]}");
        SysMovie movie = new SysMovie();
        movie.setMovieId(20L);
        movie.setMovieBoxOffice(100.0);
        when(billMapper.findByIdForUpdate(1L)).thenReturn(persisted);
        when(sessionMapper.findOneForUpdate(10L)).thenReturn(session);
        when(movieMapper.findOne(20L)).thenReturn(movie);
        when(billMapper.update(any(SysBill.class))).thenReturn(1);

        assertThrows(IllegalStateException.class,
                () -> paymentService.updateBill(paymentRequest(1L, null)));
        verify(sessionMapper, never()).update(any(SysSession.class));
        verify(movieMapper, never()).update(any(SysMovie.class));
    }

    @Test
    void billUpdateFailureStopsAllDownstreamUpdates() {
        prepareUnpaidPayment();
        when(billMapper.update(any(SysBill.class))).thenReturn(0);

        assertThrows(IllegalStateException.class,
                () -> paymentService.updateBill(paymentRequest(1L, "{\"1\":[3]}")));

        verify(sessionMapper, never()).update(any(SysSession.class));
        verify(movieMapper, never()).update(any(SysMovie.class));
    }

    @Test
    void sessionUpdateFailureThrowsSoTransactionRollsBackBill() {
        prepareUnpaidPayment();
        when(billMapper.update(any(SysBill.class))).thenReturn(1);
        when(sessionMapper.update(any(SysSession.class))).thenReturn(0);

        assertThrows(IllegalStateException.class,
                () -> paymentService.updateBill(paymentRequest(1L, "{\"1\":[3]}")));

        verify(movieMapper, never()).update(any(SysMovie.class));
    }

    @Test
    void movieUpdateFailureThrowsSoTransactionRollsBackBillAndSession() {
        prepareUnpaidPayment();
        when(billMapper.update(any(SysBill.class))).thenReturn(1);
        when(sessionMapper.update(any(SysSession.class))).thenReturn(1);
        when(movieMapper.update(any(SysMovie.class))).thenReturn(0);

        assertThrows(IllegalStateException.class,
                () -> paymentService.updateBill(paymentRequest(1L, "{\"1\":[3]}")));
    }

    private void prepareUnpaidPayment() {
        SysBill persisted = bill(1L, false, 10L, "[\"1排1号\"]");
        SysSession session = new SysSession();
        session.setSessionId(10L);
        session.setMovieId(20L);
        session.setSessionPrice(35.0);
        session.setSessionSeats("{\"1\":[0]}");
        SysMovie movie = new SysMovie();
        movie.setMovieId(20L);
        movie.setMovieBoxOffice(100.0);
        when(billMapper.findByIdForUpdate(1L)).thenReturn(persisted);
        when(sessionMapper.findOneForUpdate(10L)).thenReturn(session);
        when(movieMapper.findOne(20L)).thenReturn(movie);
    }

    private SysBillVo paymentRequest(Long billId, String sessionSeats) {
        SysBill requested = new SysBill();
        requested.setBillId(billId);
        requested.setBillState(true);
        SysBillVo request = new SysBillVo();
        request.setSysBill(requested);
        request.setSessionSeats(sessionSeats);
        return request;
    }

    private SysBill bill(Long id, boolean paid, Long sessionId, String seats) {
        SysBill bill = new SysBill();
        bill.setBillId(id);
        bill.setBillState(paid);
        bill.setSessionId(sessionId);
        bill.setSeats(seats);
        return bill;
    }
}
