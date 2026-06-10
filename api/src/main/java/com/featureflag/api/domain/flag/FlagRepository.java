package com.featureflag.api.domain.flag;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;


@Repository
public interface FlagRepository extends JpaRepository<Flag, UUID> {


    Optional<Flag> findByKeyAndEnvironment(String key, String environment);

    Page<Flag> findByEnvironment(String environment, Pageable pageable);

    boolean existsByKeyAndEnvironment(String key, String environment);

    List<Flag> findAllByEnvironment(String environment);


}
