package ru.mirea.practice7.deliveries;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import reactor.core.publisher.Mono;

import static ru.mirea.practice7.deliveries.ApiDtos.ApiError;
import static ru.mirea.practice7.deliveries.DomainExceptions.*;

@RestControllerAdvice
public class DeliveryErrorHandler {
    @ExceptionHandler(NotFound.class)
    public Mono<ResponseEntity<ApiError>> notFound(NotFound e) {
        return Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiError(e.getMessage(), "NOT_FOUND")));
    }

    @ExceptionHandler({InvalidData.class, InvalidTransition.class})
    public Mono<ResponseEntity<ApiError>> badRequest(RuntimeException e) {
        HttpStatus status = e instanceof InvalidTransition ? HttpStatus.CONFLICT : HttpStatus.BAD_REQUEST;
        return Mono.just(ResponseEntity.status(status)
                .body(new ApiError(e.getMessage(), status == HttpStatus.CONFLICT ? "INVALID_TRANSITION" : "INVALID_DATA")));
    }

    @ExceptionHandler(RemoteServiceUnavailable.class)
    public Mono<ResponseEntity<ApiError>> serviceDown(RemoteServiceUnavailable e) {
        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ApiError(e.getMessage(), "ORDER_SERVICE_UNAVAILABLE")));
    }
}
