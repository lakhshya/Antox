package im.tox.antox.fragments;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Map;

import im.tox.antox.activities.FriendProfileActivity;
import im.tox.antox.data.AntoxDB;
import im.tox.antox.utils.Constants;
import im.tox.antox.utils.Friend;
import im.tox.antox.utils.LeftPaneItem;
import im.tox.antox.R;
import im.tox.antox.tox.ToxService;
import im.tox.antox.tox.ToxSingleton;
import im.tox.antox.activities.MainActivity;
import im.tox.antox.adapters.LeftPaneAdapter;


/**
 * Created by ollie on 28/02/14.
 */
public class ContactsFragment extends Fragment {
    /**
     * List View for displaying all the friends in a scrollable list
     */
    private ListView leftPaneListView;
    /**
     * Adapter for the friendListView
     */
    private LeftPaneAdapter leftPaneAdapter;

    ToxSingleton toxSingleton = ToxSingleton.getInstance();



    public ContactsFragment() {
        main_act = (MainActivity) getActivity();

    }

    public void onChangeFriendRequest(int position, String key, String message) {
        toxSingleton.activeFriendRequestKey = key;
        toxSingleton.activeFriendKey = null;
        Fragment newFragment = new FriendRequestFragment(key, message);
        FragmentTransaction transaction = getFragmentManager().beginTransaction();
        transaction.replace(R.id.right_pane, newFragment);
        transaction.addToBackStack(null);
        transaction.commit();
    }

    public void onChangeContact(int position) {
        toxSingleton.activeFriendKey = main_act.leftPaneKeyList.get(position);
        toxSingleton.activeFriendRequestKey = null;
        ChatFragment newFragment = new ChatFragment();
        FragmentTransaction transaction = getFragmentManager().beginTransaction();
        transaction.replace(R.id.right_pane, newFragment);
        transaction.addToBackStack(null);
        transaction.commit();
    }

    public void updateLeftPane() {
        leftPaneAdapter = main_act.leftPaneAdapter;
        leftPaneListView.setAdapter(leftPaneAdapter);
        System.out.println("updated left pane");
    }

    private MainActivity main_act;
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        /**
         * Stores a 2 dimensional string array holding friend details. Will be populated
         * by a tox function once implemented
         */

        main_act = (MainActivity) getActivity();

        View rootView = inflater.inflate(R.layout.fragment_leftpane, container, false);
        leftPaneListView = (ListView) rootView.findViewById(R.id.left_pane_list);

        updateLeftPane();

        leftPaneListView
                .setOnItemClickListener(new AdapterView.OnItemClickListener() {

                    @Override
                    public void onItemClick(AdapterView<?> parent, View view, int position,
                                            long id) {
                        LeftPaneItem item = (LeftPaneItem) parent.getAdapter().getItem(position);
                        int type = item.viewType;
                        if (type == Constants.TYPE_CONTACT) {
                            if (toxSingleton.friendsList.all().size() == 0) {
                                //Final copy of position so it can be used in the inner class.
                                final int positionCopy = position;
                                //ProgressDialog to alert the user that the data is not ready.
                                final ProgressDialog progressDialog = ProgressDialog.show(getActivity(),
                                        "Loading...", "Please wait while we load your data", false, false);
                                progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);

                                //Build an async task that will cancel the dialog once the data is finished loading.
                                AsyncTask<String, Void, String> asyncTask = new AsyncTask<String, Void, String>() {
                                    @Override
                                    protected String doInBackground(String... strings) {
                                        while (toxSingleton.friendsList.all().size() == 0) {
                                            //while there are no friends, do nothing
                                        }

                                        return null;
                                    }

                                    @Override
                                    protected void onPostExecute(String s) {
                                      //When the friend list is populated then the dialog is canceled and the
                                      //right panel can be loaded.
                                      progressDialog.dismiss();
                                      onChangeContact(positionCopy);
                                    }
                                };

                                asyncTask.execute();
                            } else {
                                onChangeContact(position);
                            }
                        } else if (type == Constants.TYPE_FRIEND_REQUEST) {

                            String key = item.first;
                            String message = item.second;
                            onChangeFriendRequest(position, key, message);
                            main_act.activeTitle = main_act.getString(R.string.friendrequest);
                            main_act.pane.closePane();
                            toxSingleton.rightPaneActive = true;
                        }
                    }
                });

        leftPaneListView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {

            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View itemView, int index, long id) {
                final LeftPaneItem item = (LeftPaneItem) parent.getAdapter().getItem(index);
                final AlertDialog.Builder builder = new AlertDialog.Builder(main_act);
                boolean isGroupChat=false;
                final boolean isFriendRequest = item.viewType==Constants.TYPE_FRIEND_REQUEST;
                final CharSequence items[];

                SharedPreferences settingsPref = getActivity().getSharedPreferences("settings", Context.MODE_PRIVATE);
                final int option = settingsPref.getInt("group_option", 0);

                if(isFriendRequest){
                    items= new CharSequence[]{
                            getResources().getString(R.string.friendrequest_accept),
                            getResources().getString(R.string.friendrequest_reject),
                            getResources().getString(R.string.friend_action_block)
                    };
                } else if(option == 3) {
                    items = new CharSequence[] {
                            getResources().getString(R.string.friend_action_unblock),
                            getResources().getString(R.string.friend_action_deletechat)
                    };
                } else {
                    items= new CharSequence[]{
                            getResources().getString(R.string.friend_action_profile),
                            getResources().getString(R.string.friend_action_move_to_group),
                            getResources().getString(R.string.friend_action_delete),
                            getResources().getString(R.string.friend_action_deletechat),
                            getResources().getString(R.string.friend_action_block)
                    };
                }
                builder.setTitle(main_act.getString(R.string.contacts_actions_on) + " " + item.first)
                        .setCancelable(true)
                        .setItems(items, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int index) {
                                Log.d("picked", "" + items[index]);
                                //item.first equals the key
                                if(isFriendRequest){
                                    switch (index){
                                        case 0:
                                            AntoxDB db = new AntoxDB(getActivity().getApplicationContext());
                                            //When accepting a friend request from long click it will be automatically assigned to "Friends" group
                                            db.addFriend(item.first, "Friend Accepted", "", "", "Friends");
                                            db.close();
                                            main_act.updateLeftPane();
                                            Intent acceptRequestIntent = new Intent(getActivity(), ToxService.class);
                                            acceptRequestIntent.setAction(Constants.ACCEPT_FRIEND_REQUEST);
                                            acceptRequestIntent.putExtra("key", item.first);
                                            main_act.startService(acceptRequestIntent);
                                            main_act.updateLeftPane();
                                            break;
                                        case 1:
                                            AntoxDB antoxDB = new AntoxDB(getActivity().getApplicationContext());
                                            antoxDB.deleteFriendRequest(item.first);
                                            antoxDB.close();
                                            main_act.updateLeftPane();
                                            Intent rejectRequestIntent = new Intent(main_act, ToxService.class);
                                            rejectRequestIntent.setAction(Constants.REJECT_FRIEND_REQUEST);
                                            rejectRequestIntent.putExtra("key", item.first);
                                            main_act.startService(rejectRequestIntent);
                                            main_act.updateLeftPane();
                                            //rejectRequest(item.first);
                                            break;
                                        case 2:
                                            showBlockDialog(getActivity(),item.first);
                                            break;
                                    }
                                } else if(option == 3) {
                                    ArrayList<Friend> tmp = ((MainActivity)getActivity()).friendList;
                                    //Get friend key
                                    String key = "";
                                    for(int i = 0; i < tmp.size(); i++) {
                                        if(item.first.equals(tmp.get(i).friendName)) {
                                            key = tmp.get(i).friendKey;
                                            break;
                                        }
                                    }
                                    switch(index) {
                                        case 0:
                                            AntoxDB db = new AntoxDB(getActivity().getApplicationContext());
                                            db.unblockUser(key);
                                            db.close();
                                            break;
                                        case 1:
                                            AntoxDB db2 = new AntoxDB(getActivity().getApplicationContext());
                                            db2.deleteChat(key);
                                            db2.close();
                                            main_act.updateLeftPane();
                                            clearChat(key);
                                            break;
                                    }
                                } else {
                                    ArrayList<Friend> tmp = ((MainActivity)getActivity()).friendList;
                                    //Get friend key
                                    String key = "";
                                    String group = "";
                                    for(int i = 0; i < tmp.size(); i++) {
                                        if(item.first.equals(tmp.get(i).friendName)) {
                                            key = tmp.get(i).friendKey;
                                            group = tmp.get(i).friendGroup;
                                            break;
                                        }
                                    }
                                    switch (index){
                                        case 0:
                                            if(!key.equals("")) {
                                                Intent profile = new Intent(main_act, FriendProfileActivity.class);
                                                profile.putExtra("key", key);
                                                profile.putExtra("group", group);
                                                startActivity(profile);
                                            }
                                            break;
                                        case 1:
                                            //move to group
                                            AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(getActivity());
                                            ArrayList<String> groups = new ArrayList<String>();
                                            groups.add(getResources().getString(R.string.manage_groups_friends));
                                            SharedPreferences sharedPreferences = getActivity().getSharedPreferences("groups", Context.MODE_PRIVATE);
                                            if (!sharedPreferences.getAll().isEmpty()) {
                                                Map<String, ?> keys = sharedPreferences.getAll();
                                                for (Map.Entry<String, ?> entry : keys.entrySet()) {
                                                    groups.add(entry.getValue().toString());
                                                }
                                            }
                                            final CharSequence[] cs = groups.toArray(new CharSequence[groups.size()]);
                                            final String userKey = key;

                                            dialogBuilder.setTitle("Choose a group");
                                            dialogBuilder.setItems(cs, new DialogInterface.OnClickListener() {
                                                @Override
                                                public void onClick(DialogInterface dialogInterface, int i) {
                                                    AntoxDB db = new AntoxDB(getActivity());
                                                    db.moveUserToOtherGroup(userKey, cs[i].toString());
                                                    main_act.updateLeftPane();
                                                    Toast.makeText(getActivity(), getActivity().getString(R.string.toast_succ_move_user),
                                                            Toast.LENGTH_SHORT).show();
                                                }
                                            });
                                            builder.create().cancel();
                                            dialogBuilder.show();
                                            break;
                                        case 2:
                                            //Delete friend
                                            Log.d("ContactsFragment","Delete Friend selected");
                                            if (!key.equals("")) {
                                                showAlertDialog(getActivity(),key);
                                            }
                                            break;
                                        case 3:
                                            AntoxDB db = new AntoxDB(getActivity());
                                            db.deleteChat(key);
                                            db.close();
                                            main_act.updateLeftPane();
                                            clearChat(key);
                                            break;
                                        case 4:
                                            showBlockDialog(getActivity(),key);
                                            break;
                                    }
                                }
                                dialog.cancel();
                            }
                        });
                AlertDialog alert = builder.create();
                if(item.viewType!=Constants.TYPE_HEADER){
                    alert.show();
                }
                return true;
            }
        });

        return rootView;
    }

    public void showBlockDialog(Context context, String fkey) {
        final String key = fkey;
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setMessage(getResources().getString(R.string.friend_action_block_confirmation))
                .setCancelable(false)
                .setPositiveButton(getResources().getString(R.string.button_yes),
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog,
                                                int id) {
                                AntoxDB dbBlock = new AntoxDB(getActivity());
                                dbBlock.blockUser(key);
                                dbBlock.close();
                                clearChat(key);
                                main_act.updateLeftPane();
                            }
                        })
                .setNegativeButton(getResources().getString(R.string.button_no),
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog,
                                                int id) {
                            }
                        }
                );
        builder.show();
    }

    public void showAlertDialog(Context context, String fkey) {
        final String key= fkey;
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setMessage(getResources().getString(R.string.contacts_clear_saved_logs))
                .setCancelable(false)
                .setPositiveButton(getResources().getString(R.string.button_yes),
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog,
                                                int id) {
                                AntoxDB db = new AntoxDB(getActivity());
                                db.deleteChat(key);
                                db.deleteFriend(key);
                                db.close();
                                clearChat(key);
                                main_act.updateLeftPane();
                                Intent intent = new Intent(getActivity(), ToxService.class);
                                intent.setAction(Constants.DELETE_FRIEND_AND_CHAT);
                                intent.putExtra("key", key);
                                getActivity().startService(intent);
                            }
                        })
                .setNegativeButton(getResources().getString(R.string.button_no),
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog,
                                                int id) {
                                AntoxDB db = new AntoxDB(getActivity());
                                db.deleteFriend(key);
                                db.close();
                                clearChat(key);
                                main_act.updateLeftPane();
                                Intent intent = new Intent(getActivity(), ToxService.class);
                                intent.setAction(Constants.DELETE_FRIEND);
                                intent.putExtra("key", key);
                                getActivity().startService(intent);
                            }
                        }
                );
        builder.show();
    }
    public void clearChat(String key)
    {
        if(key.equals(toxSingleton.activeFriendKey))//check if the deleted friend was the active friend
        {
            toxSingleton.activeFriendKey=null;
            getFragmentManager().beginTransaction().
                    remove(getFragmentManager().findFragmentById(R.id.right_pane)).commit();
            main_act.activeTitle="Antox";
        }
    }
}
