package com.mtdhb.api.dao;

import java.util.List;

import javax.transaction.Transactional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

import com.mtdhb.api.constant.e.ThirdPartyApplication;
import com.mtdhb.api.entity.Cookie;
import com.mtdhb.api.entity.view.CookieRankView;

/**
 * @author i@huangdenghe.com
 * @date 2018/03/03
 */
public interface CookieRepository extends CrudRepository<Cookie, Long> {

    long countByApplicationAndValidAndUserId(ThirdPartyApplication application, boolean valid, long userId);

    Cookie findByOpenId(String openId);
    
    Cookie findByPhone(String phone);

    List<Cookie> findByUserId(long userId);

    Cookie findByIdAndUserId(long id, long userId);

    @Query("select c.userId as userId, count(*) as count from Cookie c where c.valid=true and c.application=?1 group by c.userId order by count(*) desc, max(c.gmtCreate)")
    Page<CookieRankView> findCookieRankViewByApplication(ThirdPartyApplication application, Pageable pageable);
    
    @Transactional
    void deleteByApplicationAndValidAndUserId(ThirdPartyApplication application, boolean valid, long userId);

}
