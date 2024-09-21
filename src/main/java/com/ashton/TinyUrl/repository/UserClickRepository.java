package com.ashton.TinyUrl.repository;

import com.ashton.TinyUrl.model.UserClick;
import com.ashton.TinyUrl.model.UserClickKey;
import org.springframework.data.cassandra.repository.CassandraRepository;
import org.springframework.data.cassandra.repository.Query;

public interface UserClickRepository extends CassandraRepository<UserClick, UserClickKey> {
    @Query("SELECT * FROM userclick WHERE user_name=:userName")
    Iterable<UserClick> findByUserName(String userName);

}
