package com.java_template.application.controller;

import com.java_template.application.entity.book.version_1.Book;
import com.java_template.common.service.EntityService;
import com.java_template.common.dto.EntityResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/books")
public class BookController {

    private static final Logger logger = LoggerFactory.getLogger(BookController.class);
    private final EntityService entityService;

    public BookController(EntityService entityService) {
        this.entityService = entityService;
    }

    @GetMapping
    public ResponseEntity<List<EntityResponse<Book>>> getAllBooks() {
        try {
            logger.info("Retrieving all books");
            List<EntityResponse<Book>> books = entityService.findAll(Book.class);
            return ResponseEntity.ok(books);
        } catch (Exception e) {
            logger.error("Failed to retrieve books: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<EntityResponse<Book>> getBookById(@PathVariable UUID id) {
        try {
            logger.info("Retrieving book by ID: {}", id);
            EntityResponse<Book> book = entityService.getById(id, Book.class);
            if (book == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(book);
        } catch (Exception e) {
            logger.error("Failed to retrieve book {}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/business/{bookId}")
    public ResponseEntity<EntityResponse<Book>> getBookByBusinessId(@PathVariable Long bookId) {
        try {
            logger.info("Retrieving book by business ID: {}", bookId);
            EntityResponse<Book> book = entityService.findByBusinessId(Book.class, bookId.toString(), "bookId");
            if (book == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(book);
        } catch (Exception e) {
            logger.error("Failed to retrieve book by business ID {}: {}", bookId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping
    public ResponseEntity<EntityResponse<Book>> createBook(@RequestBody Book book) {
        try {
            logger.info("Creating new book with ID: {}", book.getBookId());
            EntityResponse<Book> savedBook = entityService.save(book);
            return ResponseEntity.status(HttpStatus.CREATED).body(savedBook);
        } catch (Exception e) {
            logger.error("Failed to create book: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<EntityResponse<Book>> updateBook(
            @PathVariable UUID id,
            @RequestBody Book book,
            @RequestParam(required = false) String transition) {
        try {
            logger.info("Updating book {} with transition: {}", id, transition);
            EntityResponse<Book> updatedBook = entityService.update(id, book, transition);
            return ResponseEntity.ok(updatedBook);
        } catch (Exception e) {
            logger.error("Failed to update book {}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteBook(@PathVariable UUID id) {
        try {
            logger.info("Deleting book: {}", id);
            entityService.deleteById(id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            logger.error("Failed to delete book {}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleIllegalArgument(IllegalArgumentException e) {
        logger.warn("Invalid request: {}", e.getMessage());
        return ResponseEntity.badRequest().body(e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleGenericException(Exception e) {
        logger.error("Unexpected error: {}", e.getMessage(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("An unexpected error occurred");
    }
}
