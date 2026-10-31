package com.example.elancobackend;

import com.example.elancobackend.error.ConflictException;
import com.example.elancobackend.ingestion.IngestionScheduler;
import com.example.elancobackend.ingestion.IngestionService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IngestionSchedulerTest {

    private final IngestionService service = mock(IngestionService.class);
    private final IngestionScheduler scheduler = new IngestionScheduler(service);

    @Test
    void startupAndScheduleBothRunIngestion() {
        scheduler.onStartup();
        scheduler.refresh();

        verify(service, times(2)).runIngestion();
    }

    @Test
    void skipsQuietlyWhenARunIsAlreadyInProgress() {
        when(service.runIngestion()).thenThrow(new ConflictException("already running"));

        assertThatCode(scheduler::refresh).doesNotThrowAnyException();
        verify(service).runIngestion();
    }
}
