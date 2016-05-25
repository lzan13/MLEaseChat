package net.melove.app.easechat.conversation;

import android.app.ProgressDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.LinearLayout;
import android.widget.SimpleAdapter;

import com.hyphenate.EMCallBack;
import com.hyphenate.EMError;
import com.hyphenate.EMMessageListener;
import com.hyphenate.chat.EMClient;
import com.hyphenate.chat.EMCmdMessageBody;
import com.hyphenate.chat.EMConversation;
import com.hyphenate.chat.EMMessage;
import com.hyphenate.chat.EMTextMessageBody;

import net.melove.app.easechat.R;
import net.melove.app.easechat.communal.base.MLBaseActivity;
import net.melove.app.easechat.application.MLConstants;
import net.melove.app.easechat.communal.util.MLDate;
import net.melove.app.easechat.communal.util.MLFile;
import net.melove.app.easechat.communal.util.MLMessageUtils;
import net.melove.app.easechat.communal.widget.MLToast;
import net.melove.app.easechat.notification.MLNotifier;
import net.melove.app.easechat.communal.util.MLLog;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;

/**
 * Created by lzan13 on 2015/10/12 15:00.
 * 聊天界面，处理并显示聊天双方信息
 */
public class MLChatActivity extends MLBaseActivity implements EMMessageListener {

    // 界面控件
    private Toolbar mToolbar;
    // 消息监听器
    private EMMessageListener mMessageListener;
    // 当前聊天的 ID
    private String mChatId;
    private String mCurrUsername;
    // 当前会话对象
    private EMConversation mConversation;

    // RecyclerView 用来显示消息
    private RecyclerView mRecyclerView;
    private MLMessageAdapter mMessageAdapter;
    // RecyclerView 的布局管理器
    private LinearLayoutManager mLayoutManger;
    // 下拉刷新控件
    private SwipeRefreshLayout mSwipeRefreshLayout;

    // 需要RecyclerView 滚动到的位置
    private int mRecyclerViewItemIndex;
    // 是否需要继续滚动
    private boolean isNeedScroll = false;
    private boolean isSmoothScroll = false;
    // 当前是否在最底部
    private boolean isBottom = true;


    //自定义 Handler
    private MLHandler mHandler;

    // 聊天扩展菜单
    private LinearLayout mAttachMenuLayout;
    private GridView mAttachMenuGridView;
    // 是否发送原图
    private boolean isOrigin = true;

    // 是否为阅后即焚
    private boolean isBurn;

    // 设置每次下拉分页加载多少条
    private int mPageSize = 15;

    // 聊天内容输入框
    private EditText mInputView;
    // 表情按钮
    private View mEmotionView;
    // 发送按钮
    private View mSendView;
    // 语音按钮
    private View mVoiceView;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        initView();
        initToolbar();
        // 初始化下拉刷新
        initSwipeRefreshLayout();
        // 初始化扩展菜单
        initAttachMenuGridView();

        // 初始化当前会话对象
        initConversation();

        // 设置消息点击监听
        setItemClickListener();
    }

    /**
     * 初始化界面控件
     */
    private void initView() {
        mActivity = this;
        mMessageListener = this;
        mHandler = new MLHandler();
        // 初始化阅后即焚模式为false
        isBurn = false;

        mRootView = findViewById(R.id.ml_layout_coordinator);

        // 获取当前聊天对象的id
        mChatId = getIntent().getStringExtra(MLConstants.ML_EXTRA_CHAT_ID);
        mCurrUsername = EMClient.getInstance().getCurrentUser();

        // 初始化输入框控件，并添加输入框监听
        mInputView = (EditText) findViewById(R.id.ml_edit_chat_input);
        setTextWatcher();

        // 获取输入按钮控件对象
        mEmotionView = findViewById(R.id.ml_img_chat_emotion);
        mSendView = findViewById(R.id.ml_img_chat_send);
        mVoiceView = findViewById(R.id.ml_img_chat_voice);
        // 设置输入按钮控件的点击监听
        mEmotionView.setOnClickListener(viewListener);
        mVoiceView.setOnClickListener(viewListener);
        mSendView.setOnClickListener(viewListener);
        // 设置扩展菜单点击监听
        mAttachMenuLayout = (LinearLayout) findViewById(R.id.ml_layout_chat_attach_menu);
        mAttachMenuLayout.setOnClickListener(viewListener);
    }

    /**
     * 初始化 Toolbar 控件
     */
    private void initToolbar() {
        mToolbar = (Toolbar) findViewById(R.id.ml_widget_toolbar);
        mToolbar.setTitle(mChatId);
        setSupportActionBar(mToolbar);
        // 设置toolbar图标
        mToolbar.setNavigationIcon(R.mipmap.ic_arrow_back_white_24dp);
        // 设置Toolbar图标点击事件，Toolbar上图标的id是 -1
        mToolbar.setNavigationOnClickListener(viewListener);
    }

    /**
     * 初始化会话对象，并且根据需要加载更多消息
     */
    private void initConversation() {
        /**
         * 初始化会话对象，这里有三个参数么，
         * 第一个表示会话的当前聊天的 useranme 或者 groupid
         * 第二个是绘画类型可以为空
         * 第三个表示如果会话不存在是否创建
         */
        mConversation = EMClient.getInstance().chatManager().getConversation(mChatId, null, true);
        // 设置当前会话未读数为 0
        mConversation.markAllMessagesAsRead();
        int count = mConversation.getAllMessages().size();
        if (count < mConversation.getAllMsgCount() && count < mPageSize) {
            // 获取已经在列表中的最上边的一条消息id
            String msgId = mConversation.getAllMessages().get(0).getMsgId();
            // 分页加载更多消息，需要传递已经加载的消息的最上边一条消息的id，以及需要加载的消息的条数
            mConversation.loadMoreMsgFromDB(msgId, mPageSize - count);
        }

        String draft = MLConversationExtUtils.getConversationDraft(mConversation);
        if (!TextUtils.isEmpty(draft)) {
            mInputView.setText(draft);
        }

        // 初始化ListView控件对象
        mRecyclerView = (RecyclerView) findViewById(R.id.ml_recyclerview_message);
        // 实例化消息适配器
        mMessageAdapter = new MLMessageAdapter(mActivity, mChatId);
        /**
         * 为RecyclerView 设置布局管理器，这里使用线性布局
         * RececlerView 默认的布局管理器：
         * LinearLayoutManager          显示垂直滚动列表或水平的项目
         * GridLayoutManager            显示在一个网格项目
         * StaggeredGridLayoutManager   显示在交错网格项目
         * 自定义的布局管理器，需要继承 {@link android.support.v7.widget.RecyclerView.LayoutManager}
         *
         * add/remove items时的动画是默认启用的。
         * 自定义这些动画需要继承{@link android.support.v7.widget.RecyclerView.ItemAnimator}，
         * 并实现{@link RecyclerView#setItemAnimator(RecyclerView.ItemAnimator)}
         */
        mLayoutManger = new LinearLayoutManager(mActivity);
        // 设置 RecyclerView 显示状态固定掉底部
        mLayoutManger.setStackFromEnd(true);
        mRecyclerView.setLayoutManager(mLayoutManger);
        mRecyclerView.setAdapter(mMessageAdapter);
        /**
         *  为RecyclerView 设置滚动监听{@link MLRecyclerViewListener}
         *  主要为了监听RecyclerView 当前是否滚动到了指定的位置，然后做一些相应的操作
         */
        mRecyclerView.addOnScrollListener(new MLRecyclerViewListener());
    }

    private void initSwipeRefreshLayout() {
        // 初始化下拉刷新控件对象
        mSwipeRefreshLayout = (SwipeRefreshLayout) findViewById(R.id.ml_widget_chat_refreshlayout);
        // 设置下拉刷新控件颜色
        mSwipeRefreshLayout.setColorSchemeResources(R.color.ml_red_100, R.color.ml_blue_100, R.color.ml_orange_100);
        mSwipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        // 只有当前会话不为空时才可以下拉加载更多，否则会出现错误
                        if (mConversation.getAllMessages().size() > 0) {
                            // 加载更多消息到当前会话的内存中
                            List<EMMessage> messages = mConversation.loadMoreMsgFromDB(mConversation.getAllMessages().get(0).getMsgId(), mPageSize);
                            if (messages.size() > 0) {
                                // 调用 Adapter 刷新方法
                                refreshItemRangeInserted(0, messages.size());
                            }
                        }
                        mSwipeRefreshLayout.setRefreshing(false);
                    }
                }, 500);
            }
        });
    }

    /**
     * 初始化附件菜单网格列表，并设置网格点击监听
     */
    private void initAttachMenuGridView() {
        mAttachMenuGridView = (GridView) findViewById(R.id.ml_gridview_chat_attach_menu);
        int[] menuPhotos = {
                R.mipmap.ic_attach_photo,
                R.mipmap.ic_attach_location,
                R.mipmap.ic_attach_video,
                R.mipmap.ic_attach_file,
                R.mipmap.ic_attach_gift,
                R.mipmap.ic_attach_contacts
        };
        String[] menuTitles = {mActivity.getString(R.string.ml_photo), mActivity.getString(R.string.ml_location), mActivity.getString(R.string.ml_video), mActivity.getString(R.string.ml_file), mActivity.getString(R.string.ml_gift), mActivity.getString(R.string.ml_contacts)};
        String[] from = {"photo", "title"};
        int[] to = {R.id.ml_img_menu_photo, R.id.ml_text_menu_title};
        List<Map<String, Object>> list = new ArrayList<Map<String, Object>>();
        Map<String, Object> map = null;
        for (int i = 0; i < menuPhotos.length; i++) {
            map = new HashMap<String, Object>();
            map.put("photo", menuPhotos[i]);
            map.put("title", menuTitles[i]);
            list.add(map);
        }
        SimpleAdapter adapter = new SimpleAdapter(mActivity, list, R.layout.item_gridview_menu, from, to);
        mAttachMenuGridView.setAdapter(adapter);
        mAttachMenuGridView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                switch (position) {
                case 0:

                    // 打开选择图片界面
                    openSelectPhoto();
                    break;
                case 1:
                    // 发送位置
                    break;
                case 2:
                    break;
                case 3:
                    // 发送文件
                    openSelectFile();
                    break;
                case 4:
                    break;
                case 5:
                    break;
                }
                onAttachMenu();
            }
        });
    }

    /**
     * 设置输入框监听
     */
    private void setTextWatcher() {
        mInputView.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            /**
             * 检测输入框内容变化
             *
             * @param s 输入框内容
             */
            @Override
            public void afterTextChanged(Editable s) {
                if (s.toString().equals("")) {
                    mSendView.setVisibility(View.GONE);
                    mVoiceView.setVisibility(View.VISIBLE);
                } else {
                    mSendView.setVisibility(View.VISIBLE);
                    mVoiceView.setVisibility(View.GONE);
                }
                Timer timer = new Timer();
                timer.purge();
            }
        });
    }

    /**
     * 因为RecyclerView 不支持直接设置长按和点击监听，这里通过回调的方式去进行实现
     */
    private void setItemClickListener() {
        /**
         * 这里实现在{@link MLMessageAdapter MLOnItemClickListener}定义的接口，
         * 实现聊天信息ItemView需要操作的一些方法
         */
        mMessageAdapter.setOnItemClickListener(new MLMessageAdapter.MLOnItemClickListener() {

            /**
             * Item 点击及长按事件的处理
             * 这里Item的点击及长按监听都在自定义的
             * {@link net.melove.app.easechat.conversation.messageitem.MLMessageItem}里实现，
             * 然后通过回调将
             * {@link net.melove.app.easechat.conversation.messageitem.MLMessageItem}的操作以自定义 Action
             * 的方式传递过过来，因为聊天列表的 Item 有多种多样的，每一个 Item 弹出菜单不同，
             *
             * @param message 需要操作的 Item 的 EMMessage 对象
             * @param action  要处理的动作，比如 复制、转发、删除、撤回等
             */
            @Override
            public void onItemAction(EMMessage message, int action) {
                int position = mConversation.getAllMessages().indexOf(message);
                switch (action) {
                case MLConstants.ML_ACTION_MSG_CLICK:
                    MLLog.i("item position - %d", position);
                    break;
                case MLConstants.ML_ACTION_MSG_RESEND:
                    // 重发消息
                    resendMessage(position, message.getMsgId());
                    break;
                case MLConstants.ML_ACTION_MSG_COPY:
                    // 复制消息，只有文本类消息才可以复制
                    copyMessage(message);
                    break;
                case MLConstants.ML_ACTION_MSG_FORWARD:
                    // 转发消息
                    forwardMessage(message);
                    break;
                case MLConstants.ML_ACTION_MSG_DELETE:
                    // 删除消息
                    deleteMessage(position, message);
                    break;
                case MLConstants.ML_ACTION_MSG_RECALL:
                    // 撤回消息
                    recallMessage(message);
                    break;
                }
            }
        });
    }

    /**
     * 重发消息方法，这里重发什么都没有修改，只是重新发送一遍消息，
     * TODO 按正常的逻辑来说应该修改消息时间，或者重新创建新消息，把失败的消息删除，但是更新界面会有问题，这里偷懒下
     */
    public void resendMessage(int position, String msgId) {
        // 获取需要重发的消息
        EMMessage message = mConversation.getMessage(msgId, true);
        // 将失败的消息从 conversation对象中删除
        mConversation.removeMessage(message.getMsgId());
        refreshItemRemoved(position);
        // 更新消息时间 TODO 如果修改了消息时间，会导致conversation里有两条同一个id的消息，所以先删除，后修改时间
        message.setMsgTime(MLDate.getCurrentMillisecond());
        // 调用发送方法
        // EMClient.getInstance().chatManager().sendMessage(message);
        // 获取消息当前位置
        // int newPosition = mConversation.getMessagePosition(message);
        // 更新ui
        // refreshItemChanged(position);
        sendMessage(message);
    }

    /**
     * 复制消息到剪切板，只有 Text 文本类型的消息才能复制
     *
     * @param message 需要复制的消息对象
     */
    private void copyMessage(EMMessage message) {
        // 获取剪切板管理者
        ClipboardManager clipboardManager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        // 创建剪切板数据对象
        ClipData clipData = ClipData.newPlainText("message", ((EMTextMessageBody) message.getBody()).getMessage());
        // 将刚创建的数据对象添加到剪切板
        clipboardManager.setPrimaryClip(clipData);
        // 弹出提醒
        MLToast.rightToast(R.string.ml_toast_content_copy_success).show();
    }

    /**
     * 转发消息
     *
     * @param message 需要转发的消息对象
     */
    private void forwardMessage(EMMessage message) {

    }

    /**
     * 删除一条消息
     *
     * @param message 需要删除的消息
     */
    private void deleteMessage(int position, EMMessage message) {
        // 删除消息，此方法会同时删除内存和数据库中的数据
        mConversation.removeMessage(message.getMsgId());
        // 刷新界面
        refreshItemRemoved(position);
        // 弹出操作提示
        MLToast.rightToast(R.string.ml_toast_msg_delete_success).show();
    }

    /**
     * 撤回消息，将已经发送成功的消息进行撤回
     *
     * @param message 需要撤回的消息
     */
    private void recallMessage(final EMMessage message) {
        // 显示撤回消息操作的 dialog
        final ProgressDialog pd = new ProgressDialog(mActivity);
        pd.setMessage("正在撤回 请稍候……");
        pd.show();
        MLMessageUtils.sendRecallMessage(message, new EMCallBack() {
            @Override
            public void onSuccess() {
                // 关闭进度对话框
                pd.dismiss();
                // 更改要撤销的消息的内容，替换为消息已经撤销的提示内容
                EMMessage recallMessage = EMMessage.createSendMessage(EMMessage.Type.TXT);
                EMTextMessageBody body = new EMTextMessageBody(mActivity.getString(R.string.ml_hint_msg_recall_by_self));
                recallMessage.addBody(body);
                recallMessage.setReceipt(message.getTo());
                // 设置新消息的 msgId为撤销消息的 msgId
                recallMessage.setMsgId(message.getMsgId());
                // 设置新消息的 msgTime 为撤销消息的 mstTime
                recallMessage.setMsgTime(message.getMsgTime());
                // 设置扩展为撤回消息类型，是为了区分消息的显示
                recallMessage.setAttribute(MLConstants.ML_ATTR_RECALL, true);
                // 删除旧消息
                mConversation.removeMessage(message.getMsgId());
                // 保存新消息
                EMClient.getInstance().chatManager().saveMessage(recallMessage);
                // 更新UI
                refreshItemChanged(mConversation.getMessagePosition(message));
            }

            /**
             * 撤回消息失败
             * @param i 失败的错误码
             * @param s 失败的错误信息
             */
            @Override
            public void onError(final int i, final String s) {
                pd.dismiss();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        // 弹出错误提示
                        if (s.equals(MLConstants.ML_ERROR_S_RECALL_TIME)) {
                            MLToast.rightToast(R.string.ml_toast_msg_recall_faild_max_time).show();
                        } else {
                            MLToast.rightToast(mActivity.getResources().getString(R.string.ml_toast_msg_recall_faild) + i + "-" + s).show();
                        }
                    }
                });
            }

            @Override
            public void onProgress(int i, String s) {

            }
        });
    }

    /**
     * 设置消息扩展
     *
     * @param message 需要发送的消息
     */
    private void setMessageAttribute(EMMessage message) {
        if (isBurn) {
            // 设置消息扩展类型为阅后即焚
            message.setAttribute(MLConstants.ML_ATTR_BURN, true);
        }
    }

    /**
     * 最终调用发送信息方法
     *
     * @param message 需要发送的消息
     */
    private void sendMessage(final EMMessage message) {
        // 调用设置消息扩展方法
        setMessageAttribute(message);
        // 发送一条新消息时插入新消息的位置，这里直接用插入新消息前的消息总数来作为新消息的位置
        int position = mConversation.getAllMessages().size();
        /**
         *  调用sdk的消息发送方法，发送消息，这里不进行消息的状态监听
         *  都在各自的{@link net.melove.app.easechat.conversation.messageitem.MLMessageItem}实现监听
         */
        EMClient.getInstance().chatManager().sendMessage(message);
        // 设置消息状态回调
        message.setMessageStatusCallback(new EMCallBack() {
            @Override
            public void onSuccess() {
                MLLog.i("消息发送成功 %s", message.getMsgId());
                // 消息发送成功，刷新当前消息状态
                refreshItemChanged(mConversation.getMessagePosition(message));
            }

            @Override
            public void onError(final int i, final String s) {
                MLLog.i("消息发送失败 code: %d, error: %s", i, s);
                mActivity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        String error = "";
                        if (message.getError() == EMError.MESSAGE_INCLUDE_ILLEGAL_CONTENT) {
                            error = mActivity.getString(R.string.ml_toast_msg_have_illegal) + "-" + i + s;
                        } else if (message.getError() == EMError.GROUP_PERMISSION_DENIED) {
                            error = mActivity.getString(R.string.ml_toast_msg_not_join_group) + "-" + i + s;
                        } else {
                            error = mActivity.getString(R.string.ml_toast_msg_send_faild) + "-" + i + s;
                        }
                        MLLog.e(error);
                        MLToast.errorToast(error).show();
                    }
                });
                // 消息发送失败，刷新当前消息状态
                refreshItemChanged(mConversation.getMessagePosition(message));
            }

            @Override
            public void onProgress(int i, String s) {
                // TODO 消息发送进度，这里不处理，留给消息Item自己去更新
                MLLog.i("消息发送中 progress: %d, %s", i, s);
            }
        });
        // 点击发送后马上刷新界面，无论消息有没有成功，先刷新显示
        refreshItemInserted(position);

    }

    /**
     * 发送文本消息
     */
    private void sendTextMessage() {
        String content = mInputView.getText().toString();
        mInputView.setText("");
        // 设置界面按钮状态
        mSendView.setVisibility(View.GONE);
        mVoiceView.setVisibility(View.VISIBLE);
        // 创建一条文本消息
        EMMessage textMessage = EMMessage.createTxtSendMessage(content, mChatId);
        // 调用刷新消息的方法，
        sendMessage(textMessage);
    }

    /**
     * 发送图片消息
     *
     * @param path 要发送的图片的路径
     */
    private void sendImageMessage(String path) {
        /**
         * 根据图片路径创建一条图片消息，需要三个参数，
         * path     图片路径
         * isOrigin 是否发送原图
         * mChatId  接收者
         */
        EMMessage imgMessage = EMMessage.createImageSendMessage(path, isOrigin, mChatId);
        sendMessage(imgMessage);
    }

    /**
     * 发送文件消息
     *
     * @param path 要发送的文件的路径
     */
    private void sendFileMessage(String path) {
        /**
         * 根据文件路径创建一条文件消息
         */
        EMMessage fileMessage = EMMessage.createFileSendMessage(path, mChatId);
        sendMessage(fileMessage);

    }

    /**
     * 处理Activity的返回值得方法
     *
     * @param requestCode 请求码
     * @param resultCode  返回码
     * @param data        返回的数据
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        MLLog.d("onActivityResult requestCode %d, resultCode %d", requestCode, resultCode);
        switch (requestCode) {
        case MLConstants.ML_REQUEST_CODE_PHOTO:
            // 选择图片后返回获取返回的图片路径，然后发送图片
            if (data != null) {
                String imagePath = MLFile.getPath(mActivity, data.getData());
                if (TextUtils.isEmpty(imagePath) || !new File(imagePath).exists()) {
                    MLToast.errorToast("image is not exist").show();
                    return;
                }
                sendImageMessage(imagePath);
            }
            break;
        case MLConstants.ML_REQUEST_CODE_VIDEO:
            // 视频文件 TODO 可以考录自定义实现录制小视频
            break;
        case MLConstants.ML_REQUEST_CODE_FILE:
            // 选择文件后返回获取返回的文件路径，然后发送文件
            if (data != null) {
                String filePath = MLFile.getPath(mActivity, data.getData());
                sendFileMessage(filePath);
            }
            break;
        case MLConstants.ML_REQUEST_CODE_LOCATION:
            // TODO 发送位置消息
            break;
        case MLConstants.ML_REQUEST_CODE_GIFT:
            // TODO 发送礼物
            break;
        case MLConstants.ML_REQUEST_CODE_CONTACTS:
            // TODO 发送联系人名片
            break;
        default:
            break;
        }
    }


    /**
     * 聊天界面按钮监听事件
     */
    private View.OnClickListener viewListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            switch (v.getId()) {
            case -1:
                // 结束当前Activity
                onFinish();
                break;
            // 表情按钮
            case R.id.ml_img_chat_emotion:

                break;
            // 发送按钮
            case R.id.ml_img_chat_send:
                sendTextMessage();
                break;
            // 语音按钮
            case R.id.ml_img_chat_voice:

                break;
            // 附件菜单背景，用来关闭菜单
            case R.id.ml_layout_chat_attach_menu:
                onAttachMenu();
                break;
            }
        }
    };

    /**
     * 打开系统图片选择器，去进行选择图片
     */
    private void openSelectPhoto() {
        Intent intent = null;
        if (Build.VERSION.SDK_INT < 19) {
            intent = new Intent();
            intent.setAction(Intent.ACTION_GET_CONTENT);
            // 设置intent要选择的文件类型，这里用设置为image 图片类型
            intent.setType("image/*");
        } else {
            // 在Android 系统版本大于19 上，调用系统选择图片方法稍有不同
            intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        }
        mActivity.startActivityForResult(intent, MLConstants.ML_REQUEST_CODE_PHOTO);
    }

    /**
     * 打开系统文件选择器，去选择文件
     */
    private void openSelectFile() {
        // 设置intent属性，跳转到系统文件选择界面
        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_GET_CONTENT);
        // 设置intent要选择的文件类型，这里用 * 表示选择全部类型
        intent.setType("*/*");
        startActivityForResult(intent, MLConstants.ML_REQUEST_CODE_FILE);
    }

    /**
     * 打开和关闭附件菜单
     */
    private void onAttachMenu() {
        // 判断当前附件菜单显示状态，显示就关闭，不显示就打开
        if (mAttachMenuLayout.isShown()) {
            mAttachMenuLayout.setVisibility(View.GONE);
        } else {
            mAttachMenuLayout.setVisibility(View.VISIBLE);
        }
    }

    /**
     * 重写菜单项的选择事件
     *
     * @param item 点击的是哪一个菜单项
     * @return
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // 获取当前会话内存中的消息数量
        int count = mConversation.getAllMessages().size();

        switch (item.getItemId()) {
        case R.id.ml_action_attachment:
            // 打开或关闭附件菜单
            onAttachMenu();
            break;
        case R.id.ml_action_delete:
            // 清空会话信息，此方法只清除内存中加载的消息，并没有清除数据库中保存的消息
            // mConversation.clear();
            // 清除全部信息，包括数据库中的
            mConversation.clearAllMessages();
            refreshItemRangeRemoved(0, count);
            break;
        case R.id.ml_action_settings:

            break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_chat, menu);
        return super.onCreateOptionsMenu(menu);
    }

    /**
     * 自定义返回方法，做一些不能在 onDestroy 里做的操作
     */
    @Override
    protected void onFinish() {
        /**
         * 当会话聊天界面销毁的时候，
         * 通过{@link MLConversationExtUtils#setConversationLastTime(EMConversation)}设置会话的最后时间
         */
        MLConversationExtUtils.setConversationLastTime(mConversation);
        /**
         * 减少内存用量，这里设置为当前会话内存中多于3条时清除内存中的消息，只保留一条
         */
        if (mConversation.getAllMessages().size() > 3) {
            // 将会话内的消息从内存中清除，节省内存，但是还要在重新加载一条
            List<String> list = new ArrayList<String>();
            list.add(mConversation.getLastMessage().getMsgId());
            // 清除内存中的消息，此方法不清空DB
            mConversation.clear();
            // 加载消息到内存
            mConversation.loadMessages(list);
        }

        /**
         * 判断聊天输入框内容是否为空，不为空就保存输入框内容到{@link EMConversation}的扩展中
         * 调用{@link MLConversationExtUtils#setConversationDraft(EMConversation, String)}方法
         */
        String draft = mInputView.getText().toString().trim();
        if (!TextUtils.isEmpty(draft)) {
            // 将输入框的内容保存为草稿
            MLConversationExtUtils.setConversationDraft(mConversation, draft);
        } else {
            // 清空会话对象扩展中保存的草稿
            MLConversationExtUtils.setConversationDraft(mConversation, "");
        }

        // 这里将父类的方法在后边调用
        super.onFinish();
    }

    /**
     * 重写父类的onNewIntent方法，防止打开两个聊天界面
     *
     * @param intent 带有参数的intent
     */
    @Override
    protected void onNewIntent(Intent intent) {
        String chatId = intent.getStringExtra(MLConstants.ML_EXTRA_CHAT_ID);
        // 判断 intent 携带的数据是否是当前聊天对象
        if (mChatId.equals(chatId)) {
            super.onNewIntent(intent);
        } else {
            onFinish();
            startActivity(intent);
        }
    }

    @Override
    public void onBackPressed() {
        // super.onBackPressed();
        onFinish();
    }


    @Override
    protected void onResume() {
        super.onResume();
        // 刷新界面
        //        refreshChatUI();
        // 注册环信的消息监听器
        EMClient.getInstance().chatManager().addMessageListener(mMessageListener);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mActivity = null;
        mToolbar = null;
    }

    @Override
    protected void onStop() {
        super.onStop();
        // 再当前界面处于非活动状态时 移除消息监听
        EMClient.getInstance().chatManager().removeMessageListener(mMessageListener);
    }


    /**
     * ---------------------------- RecyclerView 刷新方法 -----------------------------------
     * 这里调用下 {@link MLMessageAdapter}里封装的方法
     * 最终还是去调用{@link android.support.v7.widget.RecyclerView.Adapter}已有的 notify 方法
     * 消息的状态改变需要调用 item changed方法
     * {@link android.support.v7.widget.RecyclerView.Adapter#notifyItemChanged(int)}
     * {@link android.support.v7.widget.RecyclerView.Adapter#notifyItemChanged(int, Object)}
     * TODO 重发消息的时候需要调用 item move
     * {@link android.support.v7.widget.RecyclerView.Adapter#notifyItemMoved(int, int)}
     * 新插入消息需要调用 item inserted
     * {@link android.support.v7.widget.RecyclerView.Adapter#notifyItemInserted(int)}
     * TODO 改变多条需要 item range changed
     * {@link android.support.v7.widget.RecyclerView.Adapter#notifyItemRangeChanged(int, int)}
     * {@link android.support.v7.widget.RecyclerView.Adapter#notifyItemRangeChanged(int, int, Object)}
     * 插入多条消息需要调用 item range inserted（加载更多消息时需要此刷新）
     * {@link android.support.v7.widget.RecyclerView.Adapter#notifyItemRangeInserted(int, int)}
     * 删除多条内容需要 item range removed（TODO 清空或者删除多条消息需要此方法）
     * {@link android.support.v7.widget.RecyclerView.Adapter#notifyItemRangeRemoved(int, int)}
     * 删除消息需要 item removed
     * {@link android.support.v7.widget.RecyclerView.Adapter#notifyItemRemoved(int)}
     */
    private void refreshAll() {
        if (mMessageAdapter != null) {
            /**
             * 发送通知前先调用{@link MLMessageAdapter#refreshMessageData()}更新{@link MLMessageAdapter}的数据源
             */
            //            mMessageAdapter.refreshMessageData();
            //            mMessageAdapter.notifyDataSetChanged();
            notifyHandler(MLConstants.ML_NOTIFY_REFRESH_ALL, 1, 1);
        }
    }

    /**
     * 改变一条 Item 的刷新
     *
     * @param position 改变的位置
     */
    private void refreshItemChanged(int position) {
        if (mMessageAdapter != null) {
            /**
             * 发送通知前先调用{@link MLMessageAdapter#refreshMessageData()}更新{@link MLMessageAdapter}的数据源
             */
            //            mMessageAdapter.refreshMessageData();
            //            mMessageAdapter.notifyItemChanged(position);
            notifyHandler(MLConstants.ML_NOTIFY_REFRESH_CHANGED, position, 1);
        }
    }

    /**
     * 改变多条 Item 的刷新
     *
     * @param position 改变的位置
     * @param count    改变的数量
     */
    private void refreshItemRangeChanged(int position, int count) {
        if (mMessageAdapter != null) {
            /**
             * 发送通知前先调用{@link MLMessageAdapter#refreshMessageData()}更新{@link MLMessageAdapter}的数据源
             */
            //            mMessageAdapter.refreshMessageData();
            //            mMessageAdapter.notifyItemRangeChanged(position, count);
            notifyHandler(MLConstants.ML_NOTIFY_REFRESH_RANGE_CHANGED, position, count);
        }
    }

    /**
     * 插入一条 Item 的刷新
     *
     * @param position 插入 Item 的位置
     */
    private void refreshItemInserted(int position) {
        if (mMessageAdapter != null) {
            /**
             * 发送通知前先调用{@link MLMessageAdapter#refreshMessageData()}更新{@link MLMessageAdapter}的数据源
             */
            //            mMessageAdapter.refreshMessageData();
            //            mMessageAdapter.notifyItemInserted(position);
            notifyHandler(MLConstants.ML_NOTIFY_REFRESH_INSERTED, position, 1);
        }
    }

    /**
     * 插入多条数据的刷新
     *
     * @param position 开始插入的位置
     * @param count    变化的数量
     */
    private void refreshItemRangeInserted(int position, int count) {
        if (mMessageAdapter != null) {
            /**
             * 发送通知前先调用{@link MLMessageAdapter#refreshMessageData()}更新{@link MLMessageAdapter}的数据源
             */
            //            mMessageAdapter.refreshMessageData();
            //            mMessageAdapter.notifyItemRangeInserted(position, count);
            notifyHandler(MLConstants.ML_NOTIFY_REFRESH_RANGE_INSERTED, position, count);
        }
    }

    /**
     * Item 位置移动的刷新
     *
     * @param formPosition 原位置
     * @param toPosition   移动后的位置
     */
    private void refreshItemMoved(int formPosition, int toPosition) {
        if (mMessageAdapter != null) {
            /**
             * 发送通知前先调用{@link MLMessageAdapter#refreshMessageData()}更新{@link MLMessageAdapter}的数据源
             */
            //            mMessageAdapter.refreshMessageData();
            //            mMessageAdapter.notifyItemMoved(formPosition, toPosition);
            notifyHandler(MLConstants.ML_NOTIFY_REFRESH_MOVED, formPosition, toPosition);
        }
    }

    /**
     * 删除一条 Item 的刷新
     *
     * @param position 删除的位置
     */
    private void refreshItemRemoved(int position) {
        if (mMessageAdapter != null) {
            /**
             * 发送通知前先调用{@link MLMessageAdapter#refreshMessageData()}更新{@link MLMessageAdapter}的数据源
             */
            //            mMessageAdapter.refreshMessageData();
            //            mMessageAdapter.notifyItemRemoved(position);
            notifyHandler(MLConstants.ML_NOTIFY_REFRESH_REMOVED, position, 1);
        }
    }

    /**
     * 删除多项 Item 的刷新
     *
     * @param position 开始位置
     * @param count    变化的数量
     */
    private void refreshItemRangeRemoved(int position, int count) {
        if (mMessageAdapter != null) {
            /**
             * 发送通知前先调用{@link MLMessageAdapter#refreshMessageData()}更新{@link MLMessageAdapter}的数据源
             */
            //            mMessageAdapter.refreshMessageData();
            //            mMessageAdapter.notifyItemRangeRemoved(position, count);
            notifyHandler(MLConstants.ML_NOTIFY_REFRESH_RANGE_REMOVED, position, count);
        }
    }

    /**
     * 发送Handler去刷新界面
     *
     * @param type 刷新类型
     * @param arg1 刷新位置
     * @param arg2 刷新数量
     */
    private void notifyHandler(int type, int arg1, int arg2) {
        /**
         * 发送通知前先调用{@link MLMessageAdapter#refreshMessageData()}更新{@link MLMessageAdapter}的数据源
         */
        mMessageAdapter.refreshMessageData();
        // 得到 Hander 的 Message
        Message msg = mHandler.obtainMessage();
        // 设置相应的参数
        msg.what = type;
        msg.arg1 = arg1;
        msg.arg2 = arg2;
        // 发送 Handler 消息
        mHandler.sendMessage(msg);
    }

    /**
     * 自定义 Handler 类，主要用于界面的刷新操作
     */
    class MLHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            int what = msg.what;
            int arg1 = msg.arg1;
            int arg2 = msg.arg2;
            switch (what) {
            case MLConstants.ML_NOTIFY_REFRESH_ALL:
                mMessageAdapter.notifyDataSetChanged();
                break;
            // 改变
            case MLConstants.ML_NOTIFY_REFRESH_CHANGED:
                mMessageAdapter.notifyItemChanged(arg1);
                break;
            case MLConstants.ML_NOTIFY_REFRESH_RANGE_CHANGED:
                mMessageAdapter.notifyItemRangeChanged(arg1, arg2);
                break;
            // 插入
            case MLConstants.ML_NOTIFY_REFRESH_INSERTED:
                mMessageAdapter.notifyItemInserted(arg1);
                // 只有当前列表在最底部的时候才向下滚动
                if (isBottom) {
                    scrollToItem(arg1, false);
                }
                break;
            case MLConstants.ML_NOTIFY_REFRESH_RANGE_INSERTED:
                mMessageAdapter.notifyItemRangeInserted(arg1, arg2);
                scrollToItem(arg2 - 1, false);
                break;
            // 移动
            case MLConstants.ML_NOTIFY_REFRESH_MOVED:
                mMessageAdapter.notifyItemMoved(arg1, arg2);
                break;
            // 删除
            case MLConstants.ML_NOTIFY_REFRESH_REMOVED:
                mMessageAdapter.notifyItemRemoved(arg1);
                break;
            case MLConstants.ML_NOTIFY_REFRESH_RANGE_REMOVED:
                mMessageAdapter.notifyItemRangeRemoved(arg1, arg2);
                break;
            }
        }
    }
    /*--------------------------------- 刷新代码结束 -----------------------------------------*/


    /**
     * ----------------------------- RecyclerView 滚动监听 -----------------------------------
     * 监听RecyclerView 的滚动，实现平滑滚动到指定位置
     *
     * @param position 需要滚动到的位置
     * @param isScroll 是否需要滚动效果
     */
    private void scrollToItem(int position, boolean isScroll) {
        if (position < 0 || position >= mMessageAdapter.getItemCount()) {
            return;
        }
        mRecyclerViewItemIndex = position;
        isSmoothScroll = isScroll;
        // 不管当前 RecyclerView 是否在滚动，都调用一下停止，进行下一次的位置滚动
        mRecyclerView.stopScroll();
        if (isScroll) {
            // 调用带有动画的滚动方法滚动 RecyclerView
            smoothScrollToItem(position);
        } else {
            scrollToItem(position);
        }
    }

    /**
     * 带有滚动效果的 Scroll 方法，将 RecyclerView 滚动到指定位置
     *
     * @param position 需要滚动到的位置
     */
    private void smoothScrollToItem(int position) {
        // RecyclerView 当前在屏幕上显示的第一个item的位置
        int firstItem = mLayoutManger.findFirstVisibleItemPosition();
        // RecyclerView 当前在屏幕上显示的最后一个item的位置
        int lastItem = mLayoutManger.findLastVisibleItemPosition();
        // 判断需要滚动到的 position 和当前显示的区别，做一些相应的操作
        if (position <= firstItem) {
            mRecyclerView.smoothScrollToPosition(position);
        } else if (position <= lastItem) {
            int top = mRecyclerView.getChildAt(position - firstItem).getTop();
            mRecyclerView.smoothScrollBy(0, top);
        } else {
            mRecyclerView.smoothScrollToPosition(position);
            isNeedScroll = true;
        }
    }

    /**
     * 将RecyclerView 滚动到指定位置，这个没有滚动的动画效果，直接跳转到相应位置
     *
     * @param position 需要滚动到的位置
     */
    private void scrollToItem(int position) {
        // RecyclerView 当前在屏幕上显示的第一个item的索引位置
        int firstItem = mLayoutManger.findFirstVisibleItemPosition();
        // RecyclerView 当前在屏幕上显示的最后一个item的索引位置
        int lastItem = mLayoutManger.findLastVisibleItemPosition();
        // 判断需要滚动到的 position 和当前显示的区别，做一些相应的操作
        if (position <= firstItem) {
            mRecyclerView.scrollToPosition(position);
        } else if (position <= lastItem) {
            int top = mRecyclerView.getChildAt(position - firstItem).getTop();
            mRecyclerView.scrollBy(0, top);
        } else {
            mRecyclerView.scrollToPosition(position);
            isNeedScroll = true;
        }
    }

    /**
     * 自定义实现RecyclerView的滚动监听，监听
     */
    class MLRecyclerViewListener extends RecyclerView.OnScrollListener {
        /**
         * 监听 RecyclerView 滚动状态的变化
         *
         * @param recyclerView 当前监听的 RecyclerView 控件
         * @param newState     RecyclerView 变化的状态
         */
        @Override
        public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
            super.onScrollStateChanged(recyclerView, newState);
            if (isNeedScroll && newState == RecyclerView.SCROLL_STATE_IDLE && isSmoothScroll) {
                isNeedScroll = false;
                int firstItem = mLayoutManger.findFirstCompletelyVisibleItemPosition();
                // int firstItem = mLayoutManger.findFirstVisibleItemPosition()();
                int n = mRecyclerViewItemIndex - firstItem;
                if (0 <= n && n < mRecyclerView.getChildCount()) {
                    int top = mRecyclerView.getChildAt(n).getTop();
                    mRecyclerView.smoothScrollBy(0, top);
                }
            }
            // 当 RecyclerView 停止滚动后判断当前是否在底部
            if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                int lastItem = mLayoutManger.findLastVisibleItemPosition();
                if (lastItem == (mMessageAdapter.getItemCount() - 1)) {
                    isBottom = true;
                } else {
                    isBottom = false;
                }
            }
            MLLog.i("onScrollStateChanged - isNeedScroll:%b, isSmoothScroll:%b, newState:%d, isBottom:%b", isNeedScroll, isSmoothScroll, newState, isBottom);
        }

        /**
         * RecyclerView 正在滚动中
         *
         * @param recyclerView 当前监听的 RecyclerView 控件
         * @param dx           水平变化值，表示水平滚动，正表示向右，负表示向左
         * @param dy           垂直变化值，表示上下滚动，正表示向下，负表示向上
         */
        @Override
        public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
            super.onScrolled(recyclerView, dx, dy);
            if (isNeedScroll && !isSmoothScroll) {
                isNeedScroll = false;
                int firstItem = mLayoutManger.findFirstCompletelyVisibleItemPosition();
                int n = mRecyclerViewItemIndex - firstItem;
                if (0 <= n && n < mRecyclerView.getChildCount()) {
                    int top = mRecyclerView.getChildAt(n).getTop();
                    mRecyclerView.scrollBy(0, top);
                }
            }
            // 如果正在向上滚动，则也设置 isBottom 状态为false
            if (dy < 0) {
                isBottom = false;
            }
            MLLog.i("onScrolled - isNeedScroll:%b, isSmoothScroll:%b, dy:%d", isNeedScroll, isSmoothScroll, dy);
        }
    }
    /*------------------------------- RecyclerView 滚动监听及处理结束 ------------------------------*/


    /**
     * --------------------------------- Message Listener -------------------------------------
     * 环信消息监听主要方法
     */
    /**
     * 收到新消息
     *
     * @param list 收到的新消息集合
     */
    @Override
    public void onMessageReceived(List<EMMessage> list) {
        MLLog.i("onMessageReceived list.size:%d", list.size());
        // 循环遍历当前收到的消息
        for (EMMessage message : list) {
            // 判断消息是否是当前会话的消息
            if (mChatId.equals(message.getFrom())) {
                // 设置消息为已读
                mConversation.markMessageAsRead(message.getMsgId());
                // 调用刷新方法，因为收到的消息是一个list集合，所以我们调用插入多条 Item 的刷新方法
                int position = mConversation.getAllMessages().size() - list.size();
                int count = list.size();
                refreshItemInserted(position);
            } else {
                // 如果消息不是当前会话的消息发送通知栏通知
                MLNotifier.getInstance(mActivity).sendNotificationMessage(message);
            }
            MLLog.i("message id:%s, from:%s, mseeage:%s", message.getMsgId(), message.getFrom(), message.toString());
        }

    }

    /**
     * 收到新的 CMD 消息
     *
     * @param list
     */
    @Override
    public void onCmdMessageReceived(List<EMMessage> list) {
        for (int i = 0; i < list.size(); i++) {
            // 透传消息
            EMMessage cmdMessage = list.get(i);
            EMCmdMessageBody body = (EMCmdMessageBody) cmdMessage.getBody();
            // 判断是不是撤回消息的透传
            if (body.action().equals(MLConstants.ML_ATTR_RECALL)) {
                MLMessageUtils.receiveRecallMessage(mActivity, cmdMessage);
                refreshAll();
            }
        }
    }

    /**
     * 收到新的已读回执
     *
     * @param list 收到消息已读回执
     */
    @Override
    public void onMessageReadAckReceived(List<EMMessage> list) {
        MLLog.i("onMessageReadAckReceived list.size:%d", list.size());
        // 调用刷新方法，因为收到的消息是一个list集合，所以我们调用多条 Item 改变的刷新方法
        int position = mConversation.getAllMessages().size() - list.size();
        int count = list.size();
        refreshItemRangeChanged(position, count);
    }

    /**
     * 收到新的发送回执
     * TODO 好像无效
     *
     * @param list 收到发送回执的消息集合
     */
    @Override
    public void onMessageDeliveryAckReceived(List<EMMessage> list) {
        MLLog.i("onMessageDeliveryAckReceived list.size:%d", list.size());
        // 调用刷新方法，因为收到的消息是一个list集合，所以我们调用多条 Item 改变的刷新方法
        int position = mConversation.getAllMessages().size() - list.size();
        int count = list.size();
        refreshItemRangeChanged(position, count);
    }

    /**
     * 消息的状态改变
     *
     * @param message 发生改变的消息
     * @param object  包含改变的消息
     */
    @Override
    public void onMessageChanged(EMMessage message, Object object) {
        MLLog.i("onMessageChanged message:%s, object:%s", message.toString(), object.toString());
        refreshItemChanged(mConversation.getMessagePosition(message));
    }
}