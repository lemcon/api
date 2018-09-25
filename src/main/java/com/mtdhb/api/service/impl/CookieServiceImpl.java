package com.mtdhb.api.service.impl;

import java.io.IOException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import javax.annotation.Resource;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;

import com.mtdhb.api.autoconfigure.ThirdPartyApplicationProperties;
import com.mtdhb.api.constant.CacheNames;
import com.mtdhb.api.constant.e.ErrorCode;
import com.mtdhb.api.constant.e.HttpService;
import com.mtdhb.api.constant.e.ReceivingStatus;
import com.mtdhb.api.constant.e.ThirdPartyApplication;
import com.mtdhb.api.dao.CookieRepository;
import com.mtdhb.api.dao.CookieUseCountRepository;
import com.mtdhb.api.dao.ReceivingRepository;
import com.mtdhb.api.dto.CookieDTO;
import com.mtdhb.api.dto.CookieRankDTO;
import com.mtdhb.api.dto.nodejs.CookieCheckDTO;
import com.mtdhb.api.entity.Cookie;
import com.mtdhb.api.entity.Receiving;
import com.mtdhb.api.entity.view.CookieRankView;
import com.mtdhb.api.entity.view.CookieUseCountView;
import com.mtdhb.api.exception.BusinessException;
import com.mtdhb.api.service.CookieService;
import com.mtdhb.api.service.NodejsService;
import com.mtdhb.api.service.UserService;
import com.mtdhb.api.util.Entities;
import com.mtdhb.api.util.Synchronizes;

import lombok.extern.slf4j.Slf4j;

/**
 * @author i@huangdenghe.com
 * @date 2018/04/15
 */
@Service
@Slf4j
public class CookieServiceImpl implements CookieService {

    private static final int CHUNK_SIZE = 1024;

    @Autowired
    private NodejsService nodejsService;
    @Autowired
    private UserService userService;
    @Autowired
    private CookieRepository cookieRepository;
    @Autowired
    private CookieUseCountRepository cookieUseCountRepository;
    @Autowired
    private ReceivingRepository receivingRepository;
    @Autowired
    private ThirdPartyApplicationProperties thirdPartyApplicationProperties;
    @Resource(name = "endpoints")
    private AtomicLong[] endpoints;
    @Resource(name = "queues")
    private List<LinkedBlockingQueue<Cookie>> queues;
    @Resource(name = "usage")
    private Map<String, Long> usage;

    @Override
    public List<CookieDTO> list(long userId) {
        List<Cookie> cookies = cookieRepository.findByUserId(userId);
        List<CookieDTO> cookieDTOs = cookies.stream().map(cookie -> {
            cookie.setNickname(Entities.decodeNickname(cookie.getNickname()));
            return cookie;
        }).map(cookie -> {
            CookieDTO cookieDTO = new CookieDTO();
            BeanUtils.copyProperties(cookie, cookieDTO);
            if (cookie.getApplication().equals(ThirdPartyApplication.MEITUAN) || cookie.getValid()) {
                cookieDTO.setValue(null);
            }
            return cookieDTO;
        }).collect(Collectors.toList());
        return cookieDTOs;
    }

    @Cacheable(cacheNames = CacheNames.COOKIE_RANK)
    @Override
    public List<CookieRankDTO> listCookieRank(ThirdPartyApplication application) {
        Page<CookieRankView> page = cookieRepository.findCookieRankViewByApplication(application,
                PageRequest.of(0, 100));
        final AtomicInteger ranking = new AtomicInteger();
        List<CookieRankDTO> cookieRankDTOs = page.map(cookieRankView -> {
            CookieRankDTO cookieRankDTO = new CookieRankDTO();
            BeanUtils.copyProperties(cookieRankView, cookieRankDTO);
            cookieRankDTO.setRanking(ranking.incrementAndGet());
            return cookieRankDTO;
        }).getContent();
        return cookieRankDTOs;
    }

    @Override
    public void load(ThirdPartyApplication application) {
        LinkedBlockingQueue<Cookie> queue = queues.get(application.ordinal());
        AtomicLong endpoint = endpoints[application.ordinal()];
        long upper = endpoint.get();
        long daily = thirdPartyApplicationProperties.getDailies()[application.ordinal()];
        Timestamp today = Timestamp.valueOf(LocalDate.now().atStartOfDay());
        log.info("upper={}, application={}, daily={}, today={}", upper, application, daily, today);
        Slice<CookieUseCountView> cookieUseCountViews = cookieUseCountRepository.findCookieUseCountView(upper,
                application, daily, today, PageRequest.of(0, CHUNK_SIZE));
        int numberOfElements = cookieUseCountViews.getNumberOfElements();
        log.info("cookieUseCountViews#size={}", numberOfElements);
        if (numberOfElements < 1) {
            return;
        }
        CookieUseCountView last = cookieUseCountViews.getContent().get(numberOfElements - 1);
        long newUpper = last.getId();
        log.info("newUpper={}", newUpper);
        // 重设端点值
        endpoint.set(newUpper);
        cookieUseCountViews.forEach(cookieUseCountView -> {
            Cookie cookie = new Cookie();
            BeanUtils.copyProperties(cookieUseCountView, cookie);
            queue.offer(cookie);
            usage.put(cookieUseCountView.getOpenId(), cookieUseCountView.getCount());
        });
    }

    @Override
    public CookieDTO save(String cookieValue, ThirdPartyApplication application, long userId) throws IOException {
        CookieCheckDTO cookieCheckDTO = nodejsService.checkCookie(cookieValue, application);
        String openId = cookieCheckDTO.getOpenid();
        Cookie cookie = cookieRepository.findByOpenId(openId);
        if (cookie != null) {
            throw new BusinessException(ErrorCode.COOKIE_EXIST, "cookieValue={}, application={}, userId={}",
                    cookieValue, application, userId);
        }
        cookie = new Cookie();
        cookie.setValue(cookieValue);
        cookie.setService(HttpService.values()[cookieCheckDTO.getService()]);
        cookie.setApplication(application);
        cookie.setOpenId(openId);
        cookie.setNickname(Entities.encodeNickname(cookieCheckDTO.getNickname()));
        cookie.setHeadImgUrl(cookieCheckDTO.getHeadimgurl());
        cookie.setValid(true);
        cookie.setUserId(userId);
        cookie.setGmtCreate(Timestamp.from(Instant.now()));
        cookieRepository.save(cookie);
        if (usage.get(openId) == null) {
            usage.put(openId, 0L);
            queues.get(application.ordinal()).offer(cookie);
        }
        CookieDTO cookieDTO = new CookieDTO();
        BeanUtils.copyProperties(cookie, cookieDTO);
        cookieDTO.setNickname(Entities.decodeNickname(cookie.getNickname()));
        return cookieDTO;
    }

    @Override
    public void delete(long cookieId, long userId) {
        Cookie cookie = cookieRepository.findByIdAndUserId(cookieId, userId);
        if (cookie == null) {
            throw new BusinessException(ErrorCode.COOKIE_NOT_EXIST, "cookieId={}, userId={}, cookie={}", cookieId,
                    userId, cookie);
        }
        ThirdPartyApplication application = cookie.getApplication();
        if (application.equals(ThirdPartyApplication.MEITUAN)) {
            throw new BusinessException(ErrorCode.COOKIE_MEITUAN_DELETE_FAILURE,
                    "cookieId={}, application={}, userId={}", cookieId, application, userId);
        }
        String userReceiveLock = Synchronizes.buildUserReceiveLock(application, userId);
        // 删除的同时不允许领取
        synchronized (userReceiveLock) {
            Receiving receiving = receivingRepository.findByApplicationAndStatusAndUserId(application,
                    ReceivingStatus.ING, userId);
            if (receiving != null) {
                throw new BusinessException(ErrorCode.COOKIE_DELETE_FAILURE,
                        "cookieId={}, status={},userId={}, receiving={}", cookieId, ReceivingStatus.ING, userId,
                        receiving);
            }
            long available = userService.getAvailable(application, userId);
            if (available < thirdPartyApplicationProperties.getDailies()[application.ordinal()]) {
                throw new BusinessException(ErrorCode.COOKIE_DELETE_EXCEPTION,
                        "cookieId={}, application={}, userId={}, available={}", cookieId, application, userId,
                        available);
            }
            cookieRepository.delete(cookie);
        }
    }

    @Override
    public void delete(ThirdPartyApplication application, boolean valid, long userId) {
        cookieRepository.deleteByApplicationAndValidAndUserId(application, valid, userId);
    }

}
