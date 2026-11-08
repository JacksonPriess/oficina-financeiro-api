package com.dinoco.oficina.financeiro.infrastructure.web;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Map;
import java.util.NoSuchElementException;

@RestControllerAdvice
public class FinanceiroExceptionHandler {

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<?> naoEncontrado() {
        return ResponseEntity.status(404).body(Map.of("mensagem", "Pagamento não encontrado."));
    }

    @ExceptionHandler({IllegalStateException.class, DataIntegrityViolationException.class})
    public ResponseEntity<?> conflito() {
        return ResponseEntity.status(409).body(Map.of("mensagem", "Operação incompatível com o pagamento existente."));
    }

    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentNotValidException.class,
            HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<?> invalido() {
        return ResponseEntity.badRequest().body(Map.of("mensagem", "Dados da solicitação inválidos."));
    }
}
