package com.mtdhb.api.service.impl;

import java.io.IOException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import javax.annotation.Resource;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import com.mtdhb.api.autoconfigure.ThirdPartyApplicationProperties;
import com.mtdhb.api.constant.CacheNames;
import com.mtdhb.api.constant.e.CookieUseStatus;
import com.mtdhb.api.constant.e.ErrorCode;
import com.mtdhb.api.constant.e.ReceivingStatus;
import com.mtdhb.api.constant.e.ReceivingType;
import com.mtdhb.api.constant.e.ThirdPartyApplication;
import com.mtdhb.api.dao.CookieRepository;
import com.mtdhb.api.dao.CookieUseCountRepository;
import com.mtdhb.api.dao.ReceivingRepository;
import com.mtdhb.api.dto.ReceivingCarouselDTO;
import com.mtdhb.api.dto.ReceivingDTO;
import com.mtdhb.api.dto.ReceivingPieDTO;
import com.mtdhb.api.dto.ReceivingTrendDTO;
import com.mtdhb.api.dto.nodejs.CookieUseStatusDTO;
import com.mtdhb.api.dto.nodejs.RedPacketDTO;
import com.mtdhb.api.dto.nodejs.RedPacketResultDTO;
import com.mtdhb.api.dto.nodejs.ResultDTO;
import com.mtdhb.api.entity.Cookie;
import com.mtdhb.api.entity.CookieUseCount;
import com.mtdhb.api.entity.Receiving;
import com.mtdhb.api.entity.view.ReceivingCarouselView;
import com.mtdhb.api.entity.view.ReceivingPieView;
import com.mtdhb.api.entity.view.ReceivingTrendView;
import com.mtdhb.api.exception.BusinessException;
import com.mtdhb.api.service.AsyncService;
import com.mtdhb.api.service.CookieService;
import com.mtdhb.api.service.NodejsService;
import com.mtdhb.api.service.ReceivingService;
import com.mtdhb.api.service.UserService;
import com.mtdhb.api.util.Entities;

import lombok.extern.slf4j.Slf4j;

/**
 * @author i@huangdenghe.com
 * @date 2018/04/17
 */
@Service
@Slf4j
public class ReceivingServiceImpl implements ReceivingService {

    @Autowired
    private AsyncService asyncService;
    @Autowired
    private CookieService cookieService;
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
    @Resource(name = "queues")
    private List<LinkedBlockingQueue<Cookie>> queues;
    @Resource(name = "usage")
    private Map<String, Long> usage;

    @Override
    public ReceivingDTO get(long receivingId, long userId) {
        Receiving receiving = receivingRepository.findByIdAndUserId(receivingId, userId);
        if (receiving == null) {
            throw new BusinessException(ErrorCode.RECEIVING_NOT_EXIST, "receivingId={}, userId={}, receving={}",
                    receivingId, userId, receiving);
        }
        ReceivingDTO receivingDTO = new ReceivingDTO();
        BeanUtils.copyProperties(receiving, receivingDTO);
        receivingDTO.setPhone(Entities.encodePhone(receivingDTO.getPhone()));
        return receivingDTO;
    }

    @Override
    public List<ReceivingDTO> list(long userId) {
        Slice<Receiving> reveivings = receivingRepository.findByUserId(userId,
                PageRequest.of(0, 10, new Sort(Sort.Direction.DESC, "id")));
        List<ReceivingDTO> receivingDTOs = reveivings.getContent().stream().map(reveiving -> {
            ReceivingDTO receivingDTO = new ReceivingDTO();
            BeanUtils.copyProperties(reveiving, receivingDTO);
            receivingDTO.setPhone(Entities.encodePhone(receivingDTO.getPhone()));
            return receivingDTO;
        }).collect(Collectors.toList());
        return receivingDTOs;
    }

    @Cacheable(cacheNames = CacheNames.RECEIVING_CAROUSEL)
    @Override
    public List<ReceivingCarouselDTO> listReceivingCarousel() {
        Slice<ReceivingCarouselView> slice = receivingRepository
                .findReceivingCarouselView(PageRequest.of(0, 10, new Sort(Sort.Direction.DESC, "gmtModified")));
        List<ReceivingCarouselDTO> carouselReceivingDTOs = slice.map(receivingCarouselView -> {
            ReceivingCarouselDTO receivingCarouselDTO = new ReceivingCarouselDTO();
            String mail = receivingCarouselView.getMail();
            int index = mail.indexOf("@");
            String prefix = mail.substring(0, index);
            prefix = prefix.length() > 4 ? prefix.substring(0, 4) : prefix;
            mail = prefix + "****";
            BeanUtils.copyProperties(receivingCarouselView, receivingCarouselDTO);
            receivingCarouselDTO.setMail(mail);
            return receivingCarouselDTO;
        }).getContent();
        return carouselReceivingDTOs;
    }

    @Cacheable(cacheNames = CacheNames.RECEIVING_TREND)
    @Override
    public List<ReceivingTrendDTO> listReceivingTrend(ThirdPartyApplication application) {
        Timestamp thisWeek = Timestamp.valueOf(LocalDate.now().atStartOfDay().minusDays(6));
        List<ReceivingTrendView> receivingTrendViews = receivingRepository.findReceivingTrendView(application,
                thisWeek);
        List<ReceivingTrendDTO> receivingTrendDTOs = receivingTrendViews.stream().map(receivingTrendView -> {
            ReceivingTrendDTO receivingTrendDTO = new ReceivingTrendDTO();
            BeanUtils.copyProperties(receivingTrendView, receivingTrendDTO);
            return receivingTrendDTO;
        }).collect(Collectors.toList());
        return receivingTrendDTOs;
    }

    @Cacheable(cacheNames = CacheNames.RECEIVING_PIE)
    @Override
    public List<ReceivingPieDTO> listReceivingPie(ThirdPartyApplication application, Timestamp gmtCreate) {
        int scale = 100000;
        List<ReceivingPieView> receivingPieViews = receivingRepository.findReceivingPieView(application, gmtCreate);
        // 过滤异常数据
        receivingPieViews = receivingPieViews.stream()
                .filter(receivingPieView -> receivingPieView.getPrice()
                        .compareTo(thirdPartyApplicationProperties.getMinimums()[application.ordinal()]) >= 0)
                .collect(Collectors.toList());
        if (receivingPieViews.size() == 0) {
            return new ArrayList<>();
        }
        double totalCount = receivingPieViews.stream().mapToDouble(ReceivingPieView::getCount).sum();
        ReceivingPieView last = receivingPieViews.remove(receivingPieViews.size() - 1);
        List<ReceivingPieDTO> receivingPieDTOs = receivingPieViews.stream().map(receivingPieView -> {
            ReceivingPieDTO receivingPieDTO = new ReceivingPieDTO();
            receivingPieDTO.setPrice(receivingPieView.getPrice());
            // 四舍五入
            long proportion = Math.round(receivingPieView.getCount() * scale / totalCount);
            receivingPieDTO.setProportion(proportion);
            return receivingPieDTO;
        }).collect(Collectors.toList());
        long other = receivingPieDTOs.stream().mapToLong(ReceivingPieDTO::getProportion).sum();
        ReceivingPieDTO receivingPieDTO = new ReceivingPieDTO();
        receivingPieDTO.setPrice(last.getPrice());
        receivingPieDTO.setProportion(scale - other);
        receivingPieDTOs.add(receivingPieDTO);
        return receivingPieDTOs;
    }

    @Override
    public ReceivingDTO save(String urlKey, String url, String phone, ThirdPartyApplication application, long userId,
            int force) {
        /*
         * 每天零点时刻的前后一段时间内需要限制领取，防止以下原因导致 cookie 使用统计出错：
         * 
         * 1. 此系统的服务器时间已达 00:00，但美团或饿了么的服务器的时间还未到 00:00
         * 2. 零点时刻要重置内存中的 cookie 使用统计数据
         * 
         * 限制领取的持续时间为 duration * 2 分钟
         */
        // TODO duration 配置
        long duration = 10;
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime today = now.toLocalDate().atStartOfDay();
        LocalDateTime tomorrow = today.plusDays(1);
        if (Duration.between(today, now).toMinutes() < duration
                || Duration.between(now, tomorrow).toMinutes() < duration) {
            throw new BusinessException(ErrorCode.SYSTEM_MAINTENANCE.getCode(),
                    String.format(ErrorCode.SYSTEM_MAINTENANCE.getMessage(),
                            tomorrow.minusMinutes(duration).toLocalTime(), today.plusMinutes(duration).toLocalTime()),
                    "now={}, today={}, tomorrow={}", now, today, tomorrow);
        }
        List<Receiving> receivings = receivingRepository.findByUrlKeyAndApplicationAndStatus(urlKey, application,
                ReceivingStatus.ING);
        if (receivings.size() > 0) {
            throw new BusinessException(ErrorCode.RECEIVE_ING, "urlKey={}, application={}, status={}, receivings={}",
                    urlKey, application, ReceivingStatus.ING, receivings);
        }
        // 饿了么存在复用红包 sn 的情况，所以这里允许用户选择是否要领取已被领过的红包
        if (force == 0) {
            receivings = receivingRepository.findByUrlKeyAndApplicationAndStatus(urlKey, application,
                    ReceivingStatus.SUCCESS);
            if (receivings.size() > 0) {
                // TODO 返回最近一次领取时间
                throw new BusinessException(ErrorCode.RECEIVING_EXIST,
                        "urlKey={}, application={}, status={}, receivings={}", urlKey, application,
                        ReceivingStatus.SUCCESS, receivings);
            }
        }
        Receiving receiving = receivingRepository.findByApplicationAndStatusAndUserId(application, ReceivingStatus.ING,
                userId);
        if (receiving != null) {
            throw new BusinessException(ErrorCode.USER_RECEIVE_WAIT,
                    "application={}, status={}, userId={}, receiving={}", application, ReceivingStatus.ING, userId,
                    receiving);
        }
        long available = userService.getAvailable(application, userId);
        if (available < 2L) {
            throw new BusinessException(ErrorCode.AVAILABLE_INSUFFICIENT, "application={}, userId={}, available={}",
                    application, userId, available);
        }
        receiving = new Receiving();
        receiving.setUrlKey(urlKey);
        receiving.setUrl(url);
        receiving.setPhone(phone);
        receiving.setApplication(application);
        receiving.setStatus(ReceivingStatus.ING);
        receiving.setUserId(userId);
        receiving.setGmtCreate(Timestamp.from(Instant.now()));
        receivingRepository.save(receiving);
        ReceivingDTO receivingDTO = new ReceivingDTO();
        BeanUtils.copyProperties(receiving, receivingDTO);
        receivingDTO.setPhone(Entities.encodePhone(receivingDTO.getPhone()));
        asyncService.dispatch(receiving, available);
        return receivingDTO;
    }

    @Override
    public void dispatch(Receiving receiving, long available) {
        // 先将领取状态设置为领取失败
        receiving.setStatus(ReceivingStatus.FAILURE);
        ThirdPartyApplication application = receiving.getApplication();
        LinkedBlockingQueue<Cookie> queue = queues.get(application.ordinal());
        int total = thirdPartyApplicationProperties.getTotals()[application.ordinal()];
        int size = queue.size();
        log.info("queue#size={}", size);
        // 小于每个链接的红包个数要重新加载
        if (size < total) {
            cookieService.load(application);
            size = queue.size();
        }
        if (size < 1) {
            log.error("receiving={}, available={}, size={}", receiving, available, size);
            receiving.setMessage(ErrorCode.COOKIE_INSUFFICIENT.getMessage());
            receiving.setGmtModified(Timestamp.from(Instant.now()));
            receivingRepository.save(receiving);
            return;
        }
        size = Math.min(size, total << 1);
        List<Cookie> cookies = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            Cookie cookie = queue.poll();
            if (cookie == null) {
                log.error("queue#poll={}", cookie);
                break;
            }
            cookies.add(cookie);
        }
        asyncService.receive(receiving, cookies, available);
    }

    @Override
    public void receive(Receiving receiving, List<Cookie> cookies, long available) {
        String url = receiving.getUrl();
        String phone = receiving.getPhone();
        ThirdPartyApplication application = receiving.getApplication();
        LinkedBlockingQueue<Cookie> queue = queues.get(application.ordinal());
        ResultDTO<RedPacketDTO> resultDTO = null;
        try {
            resultDTO = nodejsService.getHongbao(url, phone, application, available, cookies);
        } catch (IOException e) {
            // TODO IOException 先记录错误日志，cookie 待处理
            log.error("receiving={}, cookies={}, available={}", receiving, cookies, available, e);
            receiving.setMessage(e.getClass().getSimpleName());
            receiving.setGmtModified(Timestamp.from(Instant.now()));
            receivingRepository.save(receiving);
            return;
        }
        Timestamp timestamp = Timestamp.from(Instant.now());
        // 设置领取完成时间
        receiving.setGmtModified(timestamp);
        receiving.setMessage(resultDTO.getMessage());
        RedPacketDTO redPacketDTO = resultDTO.getData();
        if (redPacketDTO == null) {
            // TODO Node.js 服务抛出运行时异常才会导致 redPacketDTO 为 null，先记录错误日志，cookie 待处理
            log.error("receiving={}, cookies={}, available={}, resultDTO={}", receiving, cookies, available, resultDTO);
            receivingRepository.save(receiving);
            return;
        }
        List<CookieUseStatusDTO> cookieUseStatusDTOs = redPacketDTO.getCookies();
        if (cookieUseStatusDTOs == null) {
            // TODO Node.js 服务抛出运行时异常才会导致 cookieUseStatusDTOs 为 null，先记录错误日志，cookie 待处理
            log.error("receiving={}, cookies={}, available={}, resultDTO={}", receiving, cookies, available, resultDTO);
            receivingRepository.save(receiving);
            return;
        }
        AtomicInteger cookieUseSuccessCount = new AtomicInteger();
        Map<Long, Cookie> cookiesToMap = cookies.stream().collect(Collectors.toMap(Cookie::getId, cookie -> cookie));
        // 已使用的 cookie 处理
        List<CookieUseCount> cookieUseCounts = cookieUseStatusDTOs.stream().map(cookieUseStatusDTO -> {
            long cookieId = cookieUseStatusDTO.getId();
            CookieUseStatus status = CookieUseStatus.values()[cookieUseStatusDTO.getStatus()];
            CookieUseCount cookieUseCount = new CookieUseCount();
            cookieUseCount.setStatus(status);
            cookieUseCount.setApplication(application);
            cookieUseCount.setCookieId(cookieId);
            cookieUseCount.setReceivingId(receiving.getId());
            cookieUseCount.setReceivingUserId(receiving.getUserId());
            cookieUseCount.setGmtCreate(timestamp);
            return cookieUseCount;
        }).peek(cookieUseCount -> {
            Cookie cookie = cookiesToMap.remove(cookieUseCount.getCookieId());
            String openId = cookie.getOpenId();
            cookieUseCount.setOpenId(openId);
            cookieUseCount.setCookieUserId(cookie.getUserId());
            long n = usage.remove(openId) + 1L;
            if (n < thirdPartyApplicationProperties.getDailies()[application.ordinal()]
                    && !cookieUseCount.getStatus().equals(CookieUseStatus.INVALID)) {
                usage.put(openId, n);
                // 未达到每人每天可以领红包的次数的有效 cookie 放回队列
                queue.offer(cookie);
            }
        }).peek(cookieUseCount -> {
            if (cookieUseCount.getStatus().equals(CookieUseStatus.SUCCESS)) {
                cookieUseSuccessCount.incrementAndGet();
            }
        }).peek(cookieUseCount -> {
            if (cookieUseCount.getStatus().equals(CookieUseStatus.INVALID)) {
                cookieRepository.findById(cookieUseCount.getCookieId()).ifPresent(cookie -> {
                    cookie.setValid(false);
                    cookie.setPhone(null);
                    cookieRepository.save(cookie);
                });
            }
        }).collect(Collectors.toList());
        // TODO 可更优化为 mysql native 批量插入
        cookieUseCountRepository.saveAll(cookieUseCounts);
        // 未使用的 cookie 放回队列
        cookiesToMap.forEach((id, cookie) -> {
            queue.offer(cookie);
        });
        RedPacketResultDTO redPacketResultDTO = redPacketDTO.getResult();
        if (redPacketResultDTO != null) {
            Integer type = redPacketResultDTO.getType();
            if (type != null) {
                receiving.setType(ReceivingType.values()[type]);
            }
            receiving.setNickname(Entities.encodeNickname(redPacketResultDTO.getNickname()));
            receiving.setPrice(redPacketResultDTO.getPrice());
            receiving.setDate(redPacketResultDTO.getDate());
        }
        if (resultDTO.getCode() == 0 && cookieUseSuccessCount.get() > 1) {
            // 领取成功
            receiving.setStatus(ReceivingStatus.SUCCESS);
        }
        receivingRepository.save(receiving);
    }

}
