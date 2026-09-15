package com.shivendra.ecommerce.services;

import com.shivendra.ecommerce.exceptions.BadRequestException;
import com.shivendra.ecommerce.models.Product;
import com.shivendra.ecommerce.repositories.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SearchServiceTest {

    @Mock
    private ProductRepository productRepository;
    @InjectMocks
    private SearchService searchService;

    @Test
    void trimsQueryAndBuildsSortedPageable() {
        when(productRepository.search(eq("iphone"), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(new Product())));

        Page<Product> page = searchService.search("  iphone ", 1, 5, "price", "desc");

        assertThat(page.getContent()).hasSize(1);
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(productRepository).search(eq("iphone"), captor.capture());
        assertThat(captor.getValue().getPageNumber()).isEqualTo(1);
        assertThat(captor.getValue().getPageSize()).isEqualTo(5);
        assertThat(captor.getValue().getSort().getOrderFor("price").getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void clampsPageSizeToMaximum() {
        when(productRepository.search(any(), any(Pageable.class))).thenReturn(Page.empty());

        searchService.search("x", 0, 500, "title", "asc");

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(productRepository).search(eq("x"), captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(50);
    }

    @Test
    void rejectsBlankQueryAndUnknownSortField() {
        assertThatThrownBy(() -> searchService.search("   ", 0, 10, "title", "asc"))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> searchService.search("x", 0, 10, "passwordHash", "asc"))
                .isInstanceOf(BadRequestException.class);
    }
}
