package com.mtdhb.api.dao;

import java.sql.Timestamp;

import javax.transaction.Transactional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

import com.mtdhb.api.constant.e.CookieUseStatus;
import com.mtdhb.api.constant.e.ThirdPartyApplication;
import com.mtdhb.api.entity.CookieUseCount;
import com.mtdhb.api.entity.view.CookieUseCountView;

/**
 * @author i@huangdenghe.com
 * @date 2018/03/13
 */
public interface CookieUseCountRepository extends CrudRepository<CookieUseCount, Long> {

    long countByStatusAndApplicationAndReceivingUserIdAndGmtCreateGreaterThan(CookieUseStatus status,
            ThirdPartyApplication application, long receivingUserId, Timestamp gmtCreate);

    @Query("select c.id as id, c.value as value, c.service as service, c.application as application,"
            + " c.openId as openId, c.nickname as nickname, c.headImgUrl as headImgUrl,c.userId as userId,"
            + " c.gmtCreate as gmtCreate, c.gmtModified as gmtModified,"
            + " (select count(*) from CookieUseCount where openId=c.openId and gmtCreate>?4) as count"
            + " from Cookie c where c.valid=true and c.id<?1 and c.application=?2"
            + " and (select count(*) from CookieUseCount where openId=c.openId and gmtCreate>?4)<?3 "
            + " order by c.id desc")
    Slice<CookieUseCountView> findCookieUseCountView(long upper, ThirdPartyApplication application, long daily,
            Timestamp gmtCreate, Pageable pageable);

    @Transactional
    void deleteByStatus(CookieUseStatus status);

}
