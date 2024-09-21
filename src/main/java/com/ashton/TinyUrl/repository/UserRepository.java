package com.ashton.TinyUrl.repository;

import com.ashton.TinyUrl.model.User;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface UserRepository extends MongoRepository<User, String> {
    User findFirstByName(String name);

}
