package com.dsinnovators.ei.identity;

import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.Repository;

interface PersonRepository extends Repository<Person, Long> {

    Person save(Person person);

    Optional<Person> findById(long id);

    List<Person> findAllByOrderByDisplayNameAscIdAsc();
}
