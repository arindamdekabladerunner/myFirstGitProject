package com.amdocs.oss.aff.activity.telstra.impl;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.amdocs.oss.aff.activity.telstra.impl.base.ActivityImplementationBase;
import com.amdocs.oss.aff.activity.telstra.util.DPMUtil;
import com.amdocs.oss.aff.activity.telstra.util.GTCommonUtil;
import com.amdocs.oss.aff.activity.telstra.util.OrderContextStoreDataProvider;
import com.amdocs.oss.aff.activity.telstra.utility.ActivityImplementationUtil;
import com.amdocs.oss.aff.schema.executionplan.Activities;
import com.amdocs.oss.asmf.domainmodel.ProjectOrderInstance;
import com.amdocs.oss.common.business.components.Activity;
import com.amdocs.oss.common.business.components.ActivityExecutionPlan;
import com.amdocs.oss.common.business.components.ExecutionPlan;
import com.amdocs.oss.common.business.components.WBS;
import com.amdocs.oss.common.components.attributestore.AttributeDetails;
import com.amdocs.oss.common.components.dpm.DynamicProcessManagerFactory;
import com.amdocs.oss.common.components.dpm.activity.ActivityContext;
import com.amdocs.oss.common.components.dpm.activity.ActivityException;
import com.amdocs.oss.common.components.dpm.activity.ActivityInstanceKey;
import com.amdocs.oss.common.components.dpm.activity.ExecutionParam;
import com.amdocs.oss.common.components.dpm.engine.slm.action.Action;
import com.amdocs.oss.common.components.dpm.engine.slm.action.ActionContext;
import com.amdocs.oss.common.components.dpm.engine.slm.action.ActionExecutionParam;
import com.amdocs.oss.common.core.logging.Logger;
import com.amdocs.oss.common.core.logging.LoggerFactory;
import com.amdocs.oss.common.core.session.Session;
import com.amdocs.oss.common.core.session.SessionFactory;
import com.amdocs.oss.odo.components.ordermanager.internal.Order;
import com.amdocs.oss.odo.core.mappers.OrderValueMapper;
import com.amdocs.oss.odo.gt.utils.CommonMethods;
import com.amdocs.oss.odo.order.contextstore.OrderContextStoreFactory;
import com.amdocs.oss.server.components.pom.IProjectOrderManager;
import com.amdocs.oss.server.components.pom.ProjectOrderManagerFactory;
import com.amdocs.oss.tls.common.TLSConstants;
import com.amdocs.oss.tls.common.jeopardy.SendNotificationContext;
import com.amdocs.oss.xml.common_cbecore.v1.CharacteristicValue;
import com.amdocs.oss.xml.common_cbecore.v1.EntitySpecificationKey;
import com.amdocs.oss.xml.order.v1.ArrayOfOrderItemValue;
import com.amdocs.oss.xml.order.v1.OrderItemValue;
import com.amdocs.oss.xml.order.v1.OrderValue;

public class NTDOfflineAppointmentRequiredNotificationActionImpl implements Action{
    private static final Logger logger = LoggerFactory.getLogger(NTDOfflineAppointmentRequiredNotificationActionImpl.class);
	private static final String CANCEL_PIK_APPOINTMENT="d24bc14f-02d6-470b-939e-db2e58624028";

	@Override
	public void execute(ActionContext activityContext) {
		
        String activityID = null;
		Session session = null;
        
        String planID = null;
        String correlationId = null;
		
		private static final String subNotificationTypeText= "ATND5900 - NTD appears offline";
        
        GTCommonUtil util = GTCommonUtil.getInstance();
        DPMUtil dpmUtil = DPMUtil.getInstance();

        Map<String, String> mapProjectStore = null;
        try {
        String projectID = activityContext.getProjectID();
        IProjectOrderManager projectOrderManager = ProjectOrderManagerFactory.getInstance().createInterface();
        session = SessionFactory.createInstance();
        planID = projectOrderManager.getProjectByID(projectID, session).getPlanID();
        Activity activity = findActivityBySpecID(planID, CANCEL_PIK_APPOINTMENT);
		activityID = activity.getId();
        
        mapProjectStore = ActivityImplementationUtil.getInstance().getProjectStoreAttributesMap(projectID, session);
        
        //OrderContextStoreDataProvider orderContextStoreDataProvider = new OrderContextStoreDataProvider();

        String orderId = mapProjectStore.get(TLSConstants.Attribute.ORDERID);
        String rootOrderId = mapProjectStore.get(TLSConstants.Attribute.ROOT_ORDERID);
        String externalOrderId = mapProjectStore.get(TLSConstants.Attribute.ORDER_EXTERNAL_ID);

        OrderValue order = util.retrieveOrder(rootOrderId);
        correlationId = util.getCorrelationId(correlationId, order);

        Order contextStoreOrder =  OrderContextStoreFactory.getInstance().createInterface().retrieveOrder(orderId);
        OrderValue orderValue = OrderValueMapper.mapFrom(contextStoreOrder);
       
        ExecutionParam[] executionParams = null;
        List<ExecutionParam> paramList = new ArrayList<ExecutionParam>();
        for (OrderItemValue orderItemValue : orderValue.getOrderItem().getItem()) {

			EntitySpecificationKey entitySpecificationKey= orderItemValue.getRootOrderLine().getEntity().getDescribingSpecificationKey();

			String productCode = entitySpecificationKey.getPrimaryKey();
			if("c_Access".equalsIgnoreCase(productCode)){
				paramList.add(new ExecutionParam("version", orderItemValue.getVersion().toString()));
			}

		}
        if (null != correlationId) {
           
            paramList.add(new ExecutionParam("correlationId", correlationId));
        }
        paramList.add(new ExecutionParam("eventOperation", "queue:eomsys:generic:sendNotification"));
        paramList.add(new ExecutionParam("OrderId", orderId));
        paramList.add(new ExecutionParam("notificationType", "notifyInformationRequired"));
        paramList.add(new ExecutionParam("externalOrderId", externalOrderId));
        paramList.add(new ExecutionParam("status", "InProgress-Pending"));
        paramList.add(new ExecutionParam("statusOutCome", "Info"));
        paramList.add(new ExecutionParam("subNotificationType", subNotificationTypeText);
        paramList.add(new ExecutionParam("rootOrderId", rootOrderId));

        executionParams= paramList.toArray(new ExecutionParam[paramList.size()]);
        SendNotificationContext sendNotificationContext = new SendNotificationContext();
        sendNotificationContext.setExecutionParameters(executionParams);
        ActivityInstanceKey activityInstanceKey = new ActivityInstanceKey(activityID, planID);
        sendNotificationContext.setActivityInstanceKey(activityInstanceKey);
        sendNotificationContext.setProjectID(projectID);

        CommonActiveVOSInvocationActivity commonActiveVOSInvocationActivity = new CommonActiveVOSInvocationActivity();

        commonActiveVOSInvocationActivity.create(sendNotificationContext);
        }catch (Exception e) {
    		// TODO: handle exception
    		logger.error("Exception in NTDOfflineAppointmentRequiredNotificationActionImpl (SendNotification Method)" + e.getMessage());
    	}finally {
    		if (session != null)
    		{
    			session.close();
    		}
    	}
        
           
    
	}
	public void sendNotification(ActionContext activityContext){
		
	}
	public Activity findActivityBySpecID(String planId, String activitySpecID) {
		logger.debug("Finding activity for planId=[" + planId + "], activitySpecID=[" + activitySpecID + "]");

		Activity activity = null;
		Session session = null;

		try {
			session = SessionFactory.createInstance();

			ExecutionPlan plan = DynamicProcessManagerFactory.getInstance().createInterface().getExecutionPlanManager().getExecutionPlan(planId);
			activity = findActivityInPlanBySpecID(plan, activitySpecID, session);
		} finally {
			if (session != null)
			{
				session.close();
			}
		}
		return activity;
	}
	public Activity findActivityInPlanBySpecID(ExecutionPlan plan, String activitySpecID, Session session) {

		Activity matchingActivity = null;

		WBS[] wbsArray = ((ActivityExecutionPlan) plan).getWbs();
		matchingActivity = findActivityInWBSArrayBySpecID(wbsArray, activitySpecID, session);

		return matchingActivity;
	}
	public Activity findActivityInWBSArrayBySpecID(WBS[] wbsArray, String activitySpecID, Session session) {
		if (!(wbsArray != null && wbsArray.length != 0)) {
			return null;
		}
		Activity activity = null;
		for (WBS wbs : wbsArray) {
			activity = findActivityInWBSBySpecID(wbs, activitySpecID, session);
			if (activity != null) {
				return activity;
			}
			else if (wbs.getSubWBS() != null && wbs.getSubWBS().length != 0) {
				activity = findActivityInWBSArrayBySpecID(wbs.getSubWBS(), activitySpecID, session);

				if(activity != null){
					return activity ;
				}
			}
		}
		return activity;
	}
	public Activity findActivityInWBSBySpecID(WBS wbs, String activitySpecID, Session session) {

		for (Activity actv : wbs.getActivities())
		{
			
			if (actv.getSpecVersionID().equals(activitySpecID))
			{
				return actv;
			}
		}
		return null;
	}
}
