/**
 * Copyright &copy; 2017-2018 <a href="http://www.webcsn.com">webcsn</a> All rights reserved.
 *
 * @author hermit
 * @date 2018-04-17 10:54:58
 */
package com.wxmp.wxapi.service.impl;

import com.wxmp.core.util.wx.WxUtil;
import com.wxmp.wxapi.process.*;
import com.wxmp.wxapi.service.MyService;
import com.wxmp.wxapi.vo.Matchrule;
import com.wxmp.wxapi.vo.MsgRequest;
import com.wxmp.wxcms.domain.*;
import com.wxmp.wxcms.mapper.*;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

/**
 * 业务消息处理
 * 开发者根据自己的业务自行处理消息的接收与回复；
 */

@Service
public class MyServiceImpl implements MyService{

	@Resource
	private MsgBaseDao msgBaseDao;

	@Resource
	private MsgNewsDao msgNewsDao;

	@Resource
	private AccountMenuDao menuDao;

	@Resource
	private AccountMenuGroupDao menuGroupDao;

	@Resource
	private AccountFansDao fansDao;
	
	private Logger logger=Logger.getLogger(MyServiceImpl.class);
	/**
	 * 处理消息
	 * 开发者可以根据用户发送的消息和自己的业务，自行返回合适的消息；
	 * @param msgRequest : 接收到的消息
	 * @param mpAccount ： appId
	 */
	public String processMsg(MsgRequest msgRequest,MpAccount mpAccount){
		String msgtype = msgRequest.getMsgType();//接收到的消息类型
		String respXml = null;//返回的内容；
		if(msgtype.equals(MsgType.Text.toString())){
			/**
			 * 文本消息，一般公众号接收到的都是此类型消息
			 */
			respXml = this.processTextMsg(msgRequest,mpAccount);
		}else if(msgtype.equals(MsgType.Event.toString())){//事件消息
			/**
			 * 用户订阅公众账号、点击菜单按钮的时候，会触发事件消息
			 */
			respXml = this.processEventMsg(msgRequest,mpAccount);
			
		//其他消息类型，开发者自行处理
		}else if(msgtype.equals(MsgType.Image.toString())){//图片消息
			
		}else if(msgtype.equals(MsgType.Location.toString())){//地理位置消息
			
		}
		
		//如果没有对应的消息，默认返回订阅消息；
		if(StringUtils.isEmpty(respXml)){
			MsgText text = msgBaseDao.getMsgTextByInputCode(MsgType.SUBSCRIBE.toString());
			if(text != null){
				respXml = MsgXmlUtil.textToXml(WxMessageBuilder.getMsgResponseText(msgRequest, text));
			}
		}
		return respXml;
	}
	
	//处理文本消息
	private String processTextMsg(MsgRequest msgRequest,MpAccount mpAccount){
		String content = msgRequest.getContent();
		if(!StringUtils.isEmpty(content)){//文本消息
			String tmpContent = content.trim();
			List<MsgNews> msgNews = msgNewsDao.getRandomMsgByContent(tmpContent,mpAccount.getMsgcount());
			if(!CollectionUtils.isEmpty(msgNews)){
				return MsgXmlUtil.newsToXml(WxMessageBuilder.getMsgResponseNews(msgRequest, msgNews));
			}
		}
		return null;
	}
	
	//处理事件消息
	private String processEventMsg(MsgRequest msgRequest,MpAccount mpAccount){
		String key = msgRequest.getEventKey();
		if(MsgType.SUBSCRIBE.toString().equals(msgRequest.getEvent())){//订阅消息
			logger.info("关注者openId----------"+msgRequest.getFromUserName());
			String openId = msgRequest.getFromUserName();
			AccountFans fans = WxApiClient.syncAccountFans(openId, mpAccount);
			//用户关注微信公众号后更新粉丝表
			if (null != fans) {
				AccountFans tmpFans = fansDao.getByOpenId(openId);
				if(tmpFans == null){
					fans.setAccount(mpAccount.getAccount());
					fansDao.add(fans);
				}else{
					fans.setId(tmpFans.getId());
					fansDao.update(fans);
				}
			}
			MsgText text = msgBaseDao.getMsgTextBySubscribe();
			if(text != null){
				return MsgXmlUtil.textToXml(WxMessageBuilder.getMsgResponseText(msgRequest, text));
			}
		}else if(MsgType.UNSUBSCRIBE.toString().equals(msgRequest.getEvent())){//取消订阅消息
			MsgText text = msgBaseDao.getMsgTextByInputCode(MsgType.UNSUBSCRIBE.toString());
			if(text != null){
				return MsgXmlUtil.textToXml(WxMessageBuilder.getMsgResponseText(msgRequest, text));
			}
		}else{//点击事件消息
			if(!StringUtils.isEmpty(key)){
				/**
				 * 固定消息
				 * _fix_ ：在我们创建菜单的时候，做了限制，对应的event_key 加了 _fix_
				 * 
				 * 当然开发者也可以进行修改
				 */
				if(key.startsWith("_fix_")){
					String baseIds = key.substring("_fix_".length());
					if(!StringUtils.isEmpty(baseIds)){
						String[] idArr = baseIds.split(",");
						if(idArr.length > 1){//多条图文消息
							List<MsgNews> msgNews = msgBaseDao.listMsgNewsByBaseId(idArr);
							if(msgNews != null && msgNews.size() > 0){
								return MsgXmlUtil.newsToXml(WxMessageBuilder.getMsgResponseNews(msgRequest, msgNews));
							}
						}else{//图文消息，或者文本消息
							MsgBase msg = msgBaseDao.getById(baseIds);
							if(msg.getMsgtype().equals(MsgType.Text.toString())){
								MsgText text = msgBaseDao.getMsgTextByBaseId(baseIds);
								if(text != null){
									return MsgXmlUtil.textToXml(WxMessageBuilder.getMsgResponseText(msgRequest, text));
								}
							}else{
								List<MsgNews> msgNews = msgBaseDao.listMsgNewsByBaseId(idArr);
								if(msgNews != null && msgNews.size() > 0){
									return MsgXmlUtil.newsToXml(WxMessageBuilder.getMsgResponseNews(msgRequest, msgNews));
								}
							}
						}
					}
				}
			}
		}
		return null;
	}
	
	//发布菜单
	public JSONObject publishMenu(MpAccount mpAccount){
		//获取数据库菜单
		List<AccountMenu> menus = menuDao.listWxMenus(new AccountMenu());
		Matchrule matchrule = new Matchrule();
		String menuJson =JSONObject.fromObject(WxUtil.prepareMenus(menus,matchrule)).toString() ;
		logger.info("创建菜单传参如下:"+menuJson);
		JSONObject rstObj = WxApiClient.publishMenus(menuJson,mpAccount);//创建普通菜单
		logger.info("创建菜单返回消息如下:"+rstObj.toString());
		//以下为创建个性化菜单demo，只为男创建菜单；其他个性化需求 设置 Matchrule 属性即可
//		matchrule.setSex("1");//1-男 ；2-女
//		JSONObject rstObj = WxApiClient.publishAddconditionalMenus(menuJson,mpAccount);//创建个性化菜单
		
//		if(rstObj != null){//成功，更新菜单组
//			if(rstObj.containsKey("menu_id")){
//				menuGroupDao.updateMenuGroupDisable();
//				menuGroupDao.updateMenuGroupEnable(gid);
//			}else if(rstObj.containsKey("errcode") && rstObj.getInt("errcode") == 0){
//				menuGroupDao.updateMenuGroupDisable();
//				menuGroupDao.updateMenuGroupEnable(gid);
//			}
//		}
		return rstObj;
	}
	
	//删除菜单
	public JSONObject deleteMenu(MpAccount mpAccount){
		JSONObject rstObj = WxApiClient.deleteMenu(mpAccount);
		if(rstObj != null && rstObj.getInt("errcode") == 0){//成功，更新菜单组
			menuGroupDao.updateMenuGroupDisable();
		}
		return rstObj;
	}

	//获取用户列表
	public boolean syncAccountFansList(MpAccount mpAccount){
		String nextOpenId = null;
		AccountFans lastFans = fansDao.getLastOpenId();
		if(lastFans != null){
			nextOpenId = lastFans.getOpenId();
		}
		return doSyncAccountFansList(nextOpenId,mpAccount);
	}
	
	//同步粉丝列表(开发者在这里可以使用递归处理)
	private boolean doSyncAccountFansList(String nextOpenId,MpAccount mpAccount){
 		String url = WxApi.getFansListUrl(WxApiClient.getAccessToken(mpAccount), nextOpenId);
		logger.info("同步粉丝入参消息如下:"+url);
		JSONObject jsonObject = WxApi.httpsRequest(url, HttpMethod.POST, null);
		logger.info("同步粉丝返回消息如下:"+jsonObject.toString());
		if(jsonObject.containsKey("errcode")){
			return false;
		}
		List<AccountFans> fansList = new ArrayList<AccountFans>();
		if(jsonObject.containsKey("data")){
			if(jsonObject.getJSONObject("data").containsKey("openid")){
				JSONArray openidArr = jsonObject.getJSONObject("data").getJSONArray("openid");
				int length = openidArr.size();
				for(int i = 0; i < length ;i++){
					Object openId = openidArr.get(i);
					AccountFans fans = WxApiClient.syncAccountFans(openId.toString(), mpAccount);
					//设置公众号
					fans.setAccount(WxMemoryCacheClient.getAccount());
					fansList.add(fans);
				}
				//批处理
				fansDao.addList(fansList);
			}
		}
		return true;
	}
	
	//获取用户信息接口 - 必须是开通了认证服务，否则微信平台没有开放此功能
	public AccountFans syncAccountFans(String openId, MpAccount mpAccount ,boolean merge){
		AccountFans fans = WxApiClient.syncAccountFans(openId, mpAccount);
		if (merge && null != fans) {
			AccountFans tmpFans = fansDao.getByOpenId(openId);
			if(tmpFans == null){
				fansDao.add(fans);
			}else{
				fans.setId(tmpFans.getId());
				fansDao.update(fans);
			}
		}
		return fans;
	}
	
	//根据openid 获取粉丝，如果没有，同步粉丝
	public AccountFans getFansByOpenId(String openId,MpAccount mpAccount){
		AccountFans fans = fansDao.getByOpenId(openId);
		if(fans == null){//如果没有，添加
			fans = WxApiClient.syncAccountFans(openId, mpAccount);
			if (null != fans) {
				fansDao.add(fans);
			}
		}
		return fans;
	}
}


