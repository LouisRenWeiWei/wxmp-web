package com.wxmp.core.util.wx;

import com.wxmp.core.common.Constants;
import com.wxmp.core.spring.SpringContextHolder;
import com.wxmp.wxapi.process.MsgType;
import com.wxmp.wxapi.vo.Matchrule;
import com.wxmp.wxcms.domain.Account;
import com.wxmp.wxcms.domain.AccountMenu;
import com.wxmp.wxcms.domain.MsgNews;
import com.wxmp.wxcms.domain.MsgText;
import com.wxmp.wxcms.mapper.MsgNewsDao;
import com.wxmp.wxcms.mapper.MsgTextDao;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WxUtil {

	private static MsgTextDao msgTextDao = SpringContextHolder.getBean(MsgTextDao.class);

	private static MsgNewsDao msgNewsDao=SpringContextHolder.getBean(MsgNewsDao.class);
	/**
	 * 获取微信公众账号的菜单
	 * @param menus	菜单列表
	 * @param matchrule	个性化菜单配置
	 * @return
	 */
	public static JSONObject prepareMenus(List<AccountMenu> menus, Matchrule matchrule) {
		if(!CollectionUtils.isEmpty(menus)){
			List<AccountMenu> parentAM = new ArrayList<AccountMenu>();
			Map<Long,List<JSONObject>> subAm = new HashMap<Long,List<JSONObject>>();
			for(AccountMenu m : menus){
				if (m.getParentId().intValue() == 0) {//一级菜单
					parentAM.add(m);
				}else{//二级菜单
					if(subAm.get(m.getParentId()) == null){
						subAm.put(m.getParentId(), new ArrayList<JSONObject>());
					}
					List<JSONObject> tmpMenus = subAm.get(m.getParentId());
					tmpMenus.add(getMenuJSONObj(m));
					subAm.put(m.getParentId(), tmpMenus);
				}
			}
			JSONArray arr = new JSONArray();
			for(AccountMenu m : parentAM){
				if(subAm.get(m.getId()) != null){//有子菜单
					arr.add(getParentMenuJSONObj(m,subAm.get(m.getId())));
				}else{//没有子菜单
					arr.add(getMenuJSONObj(m));
				}
			}
			JSONObject root = new JSONObject();
			root.put("button", arr);
			root.put("matchrule", JSONObject.fromObject(matchrule).toString());
			//添加消息id
			root.put("msgs", getMsg());
			return root;
		}
		return null;
	}

	/**
	 * 此方法是构建菜单对象的；构建菜单时，对于  key 的值可以任意定义；
	 * 当用户点击菜单时，会把key传递回来；对已处理就可以了
	 * @param menu
	 * @return
	 */
	public static JSONObject getMenuJSONObj(AccountMenu menu){
		JSONObject obj = new JSONObject();
		obj.put("name", menu.getName());
		obj.put("type", menu.getMtype());
		if(Constants.MENU_NEED_KEY.contains(menu.getMtype())){//事件菜单
			if("fix".equals(menu.getEventType())){//fix 消息
				obj.put("key", "_fix_" + menu.getMsgId());//以 _fix_ 开头
			}else{
				if(StringUtils.isEmpty(menu.getInputCode())){//如果inputcode 为空，默认设置为 subscribe，以免创建菜单失败
					obj.put("key", "subscribe");
				}else{
					obj.put("key", menu.getInputCode());
				}
			}
			//存msgtype id
			obj.put("msgType", menu.getMsgType());
			obj.put("msgId", menu.getMsgId());//
		}else{//链接菜单-view
			obj.put("url", menu.getUrl());
		}
		return obj;
	}

	public static JSONObject getParentMenuJSONObj(AccountMenu menu,List<JSONObject> subMenu){
		JSONObject obj = new JSONObject();
		obj.put("name", menu.getName());
		obj.put("sub_button", subMenu);
		return obj;
	}

    /**
     * 获取消息
     * @return
     */
	public static JSONArray getMsg(){
		JSONArray arr = new JSONArray();
		List<MsgText> msgTextList = msgTextDao.getMsgTextList(new MsgText());
		List<MsgNews> msgNews = msgNewsDao.listForPage(new MsgNews());
		if(CollectionUtils.isNotEmpty(msgTextList)){
			for (MsgText text:msgTextList) {
				JSONObject obj = new JSONObject();
				obj.put("id",text.getBaseId());
				obj.put("title",text.getTitle());
				obj.put("type", MsgType.Text.toString());
				arr.add(obj);
			}
		}
		if(CollectionUtils.isNotEmpty(msgNews)){
			for (MsgNews news:msgNews) {
				JSONObject obj = new JSONObject();
				obj.put("id",news.getBaseId());
				obj.put("title",news.getTitle());
				obj.put("type", MsgType.News.toString());
				arr.add(obj);
			}
		}
		return arr;
	}

    /**
     * 构造当前菜单数据
     *
     * @param list
     * @return
     */
    public static JSONObject getAccount(List<Account> list, String curentName) {
        JSONObject obj = new JSONObject();
        obj.put("correct", curentName);
        JSONArray arr = new JSONArray();
        if (CollectionUtils.isNotEmpty(list)) {
            for (Account account : list) {
                JSONObject objAccount = new JSONObject();
                objAccount.put("id", account.getId());
                objAccount.put("name", account.getName());
                arr.add(objAccount);
            }
        }
        obj.put("list", arr);
        return obj;
    }
}
