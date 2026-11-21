package com.dsinnovators.ei.identity;

import org.springframework.data.repository.Repository;

interface PersonRepository extends Repository<Person, Long> {

    Person save(Person person);
}
