package com.arshh.codemind.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for persistent RepositoryEntity operations.
 */
@Repository
public interface JpaRepositoryRecordStore extends JpaRepository<RepositoryEntity, String> {
}
