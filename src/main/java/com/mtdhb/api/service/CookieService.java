package com.mtdhb.api.service;

import java.io.IOException;
import java.util.List;

import com.mtdhb.api.constant.e.ThirdPartyApplication;
import com.mtdhb.api.dto.CookieDTO;
import com.mtdhb.api.dto.CookieRankDTO;

/**
 * @author i@huangdenghe.com
 * @date 2018/04/15
 */
public interface CookieService {

    List<CookieDTO> list(long userId);

    List<CookieRankDTO> listCookieRank(ThirdPartyApplication applicaton);

    void load(ThirdPartyApplication application);

    CookieDTO save(String value, ThirdPartyApplication application, long userId) throws IOException;

    void delete(long cookieId, long userId);
    
    void delete(ThirdPartyApplication application, boolean valid, long userId);

}
