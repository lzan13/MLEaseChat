package net.melove.demo.chat.fragment;


import android.app.Activity;
import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.easemob.EMCallBack;
import com.easemob.chat.EMConversation;

import net.melove.demo.chat.R;
import net.melove.demo.chat.db.MLTestHelper;
import net.melove.demo.chat.sdkutils.MLSDKHelper;
import net.melove.demo.chat.util.MLLog;

/**
 * 测试Fragment，
 * 继承自自定义的MLBaseFramgnet类，为了减少代码量，在MLBaseFrament类中定义接口回调
 * 包含此Fragment的活动窗口必须实现{@link MLBaseFragment.OnMLFragmentListener}接口,
 * 定义创建实例的工厂方法 {@link MLDrawerFragment#newInstance}，可使用此方法创建实例
 */
public class MLTestFragment extends MLBaseFragment {

    private Activity mActivity;
    private static final String ARG_PARAM1 = "param1";
    private static final String ARG_PARAM2 = "param2";

    private String mParam1;
    private String mParam2;


    /**
     * 使用这个工厂方法创建一个新的实例
     *
     * @param param1 Parameter 1.
     * @param param2 Parameter 2.
     * @return 一个新的Fragment MLTestFragment.
     */
    public static MLTestFragment newInstance(String param1, String param2) {
        MLTestFragment fragment = new MLTestFragment();
        Bundle args = new Bundle();
        args.putString(ARG_PARAM1, param1);
        args.putString(ARG_PARAM2, param2);
        fragment.setArguments(args);
        return fragment;
    }

    public MLTestFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            mParam1 = getArguments().getString(ARG_PARAM1);
            mParam2 = getArguments().getString(ARG_PARAM2);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_test, container, false);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mActivity = getActivity();
        init();
    }

    private void init() {
        getView().findViewById(R.id.ml_btn_signout).setOnClickListener(viewListener);
        getView().findViewById(R.id.ml_btn_new_conversation).setOnClickListener(viewListener);
    }


    private void signOut() {
        MLSDKHelper.getInstance().signOut(new EMCallBack() {
            @Override
            public void onSuccess() {
                mActivity.finish();
            }

            @Override
            public void onError(int i, String s) {

            }

            @Override
            public void onProgress(int i, String s) {

            }
        });
    }

    private void testCreateConversation() {
//        EMConversation conversation = new EMConversation("1435906329658", true);
//        conversation.setExtField("测试qun");
//        String s = conversation.getExtField();
//        boolean b = conversation.getIsGroup();
//        MLLog.d(s);
//        MLLog.d("b " + b);
        ContentValues values = new ContentValues();
        values.put("groupname", "1435906329658");
        values.put("groupname", "test group");
        values.put("conversation_type", 1);

        SQLiteDatabase db = MLTestHelper.getInstance(mActivity).getReadableDatabase();
        if (db.isOpen()) {
            long result = db.insert("conversation_list", null, values);
            MLLog.d("result " + result);
        }

    }

    private View.OnClickListener viewListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            switch (v.getId()) {
                case R.id.ml_btn_signout:
                    signOut();
                    break;
                case R.id.ml_btn_new_conversation:
                    testCreateConversation();
                    break;
            }
        }
    };
}
