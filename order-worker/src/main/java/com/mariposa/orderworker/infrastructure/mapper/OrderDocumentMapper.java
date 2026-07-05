package com.mariposa.orderworker.infrastructure.mapper;

import com.mariposa.orderworker.domain.model.Order;
import com.mariposa.orderworker.domain.model.Item;
import com.mariposa.orderworker.domain.model.Summary;
import com.mariposa.orderworker.infrastructure.repository.OrderDocument;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface OrderDocumentMapper {
    @Mapping(target = "id", ignore = true)
    OrderDocument toDocument(Order order);

    OrderDocument.ItemDocument toItemDocument(Item item);
    OrderDocument.SummaryDocument toSummaryDocument(Summary summary);
}