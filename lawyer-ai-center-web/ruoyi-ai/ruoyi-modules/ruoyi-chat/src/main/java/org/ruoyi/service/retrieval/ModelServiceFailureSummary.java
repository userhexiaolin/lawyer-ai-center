package org.ruoyi.service.retrieval;

import org.ruoyi.common.core.exception.ServiceException;
import java.util.concurrent.TimeoutException;

/** Only controlled categories reach logs; third-party exception messages may contain private inputs. */
public final class ModelServiceFailureSummary {
    private ModelServiceFailureSummary() { }
    public static String category(Throwable error) {
        Throwable current = error;
        String rootType = "UNKNOWN";
        for (int depth=0; current!=null && depth<8; depth++,current=current.getCause()) {
            rootType=current.getClass().getSimpleName();
            if(current instanceof TimeoutException) return "VECTOR_TIMEOUT";
            if(current instanceof ServiceException service) {
                String detail=service.getDetailMessage();
                if(detail!=null && detail.matches("TEI_(HTTP_[1-5][0-9]{2}|TIMEOUT|CONNECTION|INVALID_RESPONSE|CONFIGURATION|TRANSPORT|INTERRUPTED|VECTOR_COUNT_MISMATCH|DIMENSION_MISMATCH|INVALID_VECTOR|ZERO_VECTOR)")) return detail;
            }
        }
        return rootType;
    }
}
