package com.ashton.TinyUrl.controller;

import com.ashton.TinyUrl.model.NewTinyRequest;
import com.ashton.TinyUrl.model.User;
import com.ashton.TinyUrl.model.UserClickOut;
import com.ashton.TinyUrl.repository.UserClickRepository;
import com.ashton.TinyUrl.repository.UserRepository;
import com.ashton.TinyUrl.service.Redis;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;

import java.util.Date;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

import static com.ashton.TinyUrl.model.User.UserBuilder.anUser;
import static com.ashton.TinyUrl.model.UserClick.UserClickBuilder.anUserClick;
import static com.ashton.TinyUrl.model.UserClickKey.UserClickKeyBuilder.anUserClickKey;
import static com.ashton.TinyUrl.util.Dates.getCurMonth;
import static org.springframework.data.util.StreamUtils.createStreamFromIterator;

@RestController
public class AppController {

    @Autowired
    Redis redis;

    @Autowired
    ObjectMapper om;

    @Value("${base.url}")
    String baseUrl;

    private static final int MAX_RETRIES = 5;

    private static final int TINY_LENGTH = 6;

    Random random = new Random();

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private UserClickRepository userClickRepository;

    @RequestMapping(value = "/user", method = RequestMethod.POST)
    public User createUser(@RequestParam String name) {
        User user = anUser().withName(name).build();
        user = userRepository.insert(user);
        return user;
    }

    @RequestMapping(value = "/user/{name}", method = RequestMethod.GET)
    public User getUser(@PathVariable String name) {
        User user = userRepository.findFirstByName(name);
        return user;
    }

    private void incrementMongoField(String userName, String key){
        Query query = Query.query(Criteria.where("name").is(userName));
        Update update = new Update().inc(key, 1);
        mongoTemplate.updateFirst(query, update, "users");
    }


    @RequestMapping(value = "/tiny", method = RequestMethod.POST)
    public String generate(@RequestBody NewTinyRequest request) throws JsonProcessingException {
        String tinyCode = generateTinyCode();
        int i = 0;
        while (!redis.set(tinyCode, om.writeValueAsString(request)) && i < MAX_RETRIES) {
            tinyCode = generateTinyCode();
            i++;
        }
        if (i == MAX_RETRIES) throw new RuntimeException("SPACE IS FULL");
        return baseUrl + tinyCode + "/";
    }

    @RequestMapping(value = "/{tiny}/", method = RequestMethod.GET)
    public ModelAndView getTiny(@PathVariable String tiny) throws JsonProcessingException {
        Object tinyRequestStr = redis.get(tiny);
        NewTinyRequest tinyRequest = om.readValue(tinyRequestStr.toString(),NewTinyRequest.class);
        if (tinyRequest.getLongUrl() != null) {
            String userName = tinyRequest.getUserName();
            if ( userName != null) {
                incrementMongoField(userName, "allUrlClicks");
                incrementMongoField(userName,
                        "shorts."  + tiny + ".clicks." + getCurMonth());
                userClickRepository.save(anUserClick().userClickKey(anUserClickKey().withUserName(userName).withClickTime(new Date()).build())
                        .tiny(tiny).longUrl(tinyRequest.getLongUrl()).build());
            }
            return new ModelAndView("redirect:" + tinyRequest.getLongUrl());
        } else {
            throw new RuntimeException(tiny + " not found");
        }
    }

    @RequestMapping(value = "/user/{name}/clicks", method = RequestMethod.GET)
    public List<UserClickOut> getUserClicks(@RequestParam String name) {
        var userClicks = createStreamFromIterator( userClickRepository.findByUserName(name).iterator())
                .map(userClick -> UserClickOut.of(userClick))
                .collect(Collectors.toList());
        return userClicks;
    }

    private String generateTinyCode() {
        String charPool = "ABCDEFHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder res = new StringBuilder();
        for (int i = 0; i < TINY_LENGTH; i++) {
            res.append(charPool.charAt(random.nextInt(charPool.length())));
        }
        return res.toString();
    }
}
