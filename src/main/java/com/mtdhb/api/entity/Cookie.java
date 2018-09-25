package com.mtdhb.api.entity;

import java.sql.Timestamp;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;

import com.mtdhb.api.constant.e.HttpService;
import com.mtdhb.api.constant.e.ThirdPartyApplication;

import lombok.Data;

/**
 * @author i@huangdenghe.com
 * @date 2018/03/03
 */
@Data
@Entity
public class Cookie {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String value;
    @Enumerated
    private HttpService service;
    @Enumerated
    private ThirdPartyApplication application;
    private String openId;
    private String nickname;
    private String headImgUrl;
    @Column(name = "is_valid")
    private Boolean valid;
    private Long userId;
    private Timestamp gmtCreate;
    private Timestamp gmtModified;

}
