package im.tox.antox.tox;

import android.app.IntentService;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.support.v4.app.NotificationCompat;
import android.app.Notification;
import android.support.v4.app.TaskStackBuilder;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import java.util.ArrayList;
import java.util.Random;

import im.tox.antox.data.AntoxDB;
import im.tox.antox.utils.AntoxFriend;
import im.tox.antox.utils.Constants;
import im.tox.antox.utils.Friend;
import im.tox.antox.utils.FriendRequest;
import im.tox.antox.utils.Message;
import im.tox.antox.R;
import im.tox.antox.activities.MainActivity;
import im.tox.antox.callbacks.AntoxOnFriendRequestCallback;
import im.tox.antox.callbacks.AntoxOnMessageCallback;
import im.tox.antox.utils.UserDetails;
import im.tox.jtoxcore.FriendExistsException;
import im.tox.jtoxcore.ToxException;

public class ToxService extends IntentService {
    private static final String TAG = "im.tox.antox.tox.ToxService";
    private ToxSingleton toxSingleton;

    public ToxService() {
        super("ToxService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {

        toxSingleton = ToxSingleton.getInstance();
        toxSingleton.mNotificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        String action = intent.getAction();

        switch (action) {
            case Constants.ADD_FRIEND:
                try {
                    String[] friendData = intent.getStringArrayExtra("friendData");
                    toxSingleton.jTox.addFriend(friendData[0], friendData[1]);
                } catch (FriendExistsException e) {
                    Log.d(TAG, "Friend already exists");
                    e.printStackTrace();
                } catch (ToxException e) {
                    Log.d(TAG, "ToxException: " + e.getError().toString());
                    e.printStackTrace();
                }
                break;

            case Constants.UPDATE_SETTINGS:
                try {
                    toxSingleton.jTox.setName(UserDetails.username);
                    toxSingleton.jTox.setUserStatus(UserDetails.status);
                    toxSingleton.jTox.setStatusMessage(UserDetails.note);
                } catch (ToxException e) {
                    e.printStackTrace();
                }
                break;

            case Constants.ON_MESSAGE:
                Log.d(TAG, "Constants.ON_MESSAGE");
                String key = intent.getStringExtra(AntoxOnMessageCallback.KEY);
                String message = intent.getStringExtra(AntoxOnMessageCallback.MESSAGE);
                String name = toxSingleton.friendsList.getById(key).getName();
                int friend_number = intent.getIntExtra(AntoxOnMessageCallback.FRIEND_NUMBER, -1);
                AntoxDB db = new AntoxDB(getApplicationContext());
                if(!db.isFriendBlocked(key))
                    db.addMessage(-1, key, message, false, true, false, true);
                db.close();
            /* Broadcast */
                Intent notify = new Intent(Constants.BROADCAST_ACTION);
                notify.putExtra("action", Constants.UPDATE_MESSAGES);
                notify.putExtra("key", key);
                LocalBroadcastManager.getInstance(this).sendBroadcast(notify);
            /* Notifications */
                if (!(toxSingleton.rightPaneActive && toxSingleton.activeFriendKey.equals(key))
                        && !(toxSingleton.leftPaneActive)) {
                    Log.d(TAG, "right pane active = " + toxSingleton.rightPaneActive + ", activeFriendkey = " + toxSingleton.activeFriendKey + ", key = " + key);
                /* Notification */
                    NotificationCompat.Builder mBuilder =
                            new NotificationCompat.Builder(this)
                                    .setSmallIcon(R.drawable.ic_actionbar)
                                    .setContentTitle(name)
                                    .setContentText(message)
                                    .setDefaults(Notification.DEFAULT_ALL);
                    // Creates an explicit intent for an Activity in your app
                    Intent resultIntent = new Intent(this, MainActivity.class);
                    resultIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    resultIntent.setAction(Constants.SWITCH_TO_FRIEND);
                    resultIntent.putExtra("key", key);
                    resultIntent.putExtra("name", name);

                    // The stack builder object will contain an artificial back stack for the
                    // started Activity.
                    // This ensures that navigating backward from the Activity leads out of
                    // your application to the Home screen.
                    TaskStackBuilder stackBuilder = TaskStackBuilder.create(this);
                    // Adds the back stack for the Intent (but not the Intent itself)
                    stackBuilder.addParentStack(MainActivity.class);
                    // Adds the Intent that starts the Activity to the top of the stack
                    stackBuilder.addNextIntent(resultIntent);
                    PendingIntent resultPendingIntent =
                            stackBuilder.getPendingIntent(
                                    0,
                                    PendingIntent.FLAG_UPDATE_CURRENT
                            );
                    mBuilder.setContentIntent(resultPendingIntent);
                    toxSingleton.mNotificationManager.notify(friend_number, mBuilder.build());
                }
                break;

            case Constants.DELETE_FRIEND:
                Log.d(TAG, "Constants.DELETE_FRIEND");
                key = intent.getStringExtra("key");
                boolean wasException = false;
                // Remove friend from tox friend list
                AntoxFriend friend = toxSingleton.friendsList.getById(key);
                if(friend != null) {

                    try {
                        toxSingleton.jTox.deleteFriend(friend.getFriendnumber());
                    } catch (ToxException e) {
                        wasException = true;
                        Log.d(TAG, e.getError().toString());
                        e.printStackTrace();
                    }
                    Log.d(TAG, "Friend deleted from tox list. New size: " + toxSingleton.friendsList.all().size());
                    if (!wasException) {
                        //Delete friend from list
                        toxSingleton.friendsList.removeFriend(friend.getFriendnumber());
                        //Broadcast to update left pane
                        notify = new Intent(Constants.BROADCAST_ACTION);
                        notify.putExtra("action", Constants.UPDATE_LEFT_PANE);
                        notify.putExtra("key", key);
                        LocalBroadcastManager.getInstance(this).sendBroadcast(notify);
                    }
                }
                break;

            case Constants.DELETE_FRIEND_AND_CHAT:
                Log.d(TAG, "Constants.DELETE_FRIEND");
                key = intent.getStringExtra("key");
                wasException = false;
                // Remove friend from tox friend list
                friend = toxSingleton.friendsList.getById(key);
                if(friend != null) {

                    try {
                        toxSingleton.jTox.deleteFriend(friend.getFriendnumber());
                    } catch (ToxException e) {
                        wasException = true;
                        Log.d(TAG, e.getError().toString());
                        e.printStackTrace();
                    }
                    Log.d(TAG, "Friend deleted from tox list. New size: " + toxSingleton.friendsList.all().size());
                    if (!wasException) {
                        //Delete friend from list
                        toxSingleton.friendsList.removeFriend(friend.getFriendnumber());
                        //Broadcast to update left pane
                        notify = new Intent(Constants.BROADCAST_ACTION);
                        notify.putExtra("action", Constants.UPDATE_LEFT_PANE);
                        notify.putExtra("key", key);
                        LocalBroadcastManager.getInstance(this).sendBroadcast(notify);
                    }
                }
                break;

            case Constants.SEND_UNSENT_MESSAGES:
                Log.d(TAG, "Constants.SEND_UNSENT_MESSAGES");
                db = new AntoxDB(getApplicationContext());
                ArrayList<Message> unsentMessageList = db.getUnsentMessageList();
                Log.d(TAG, "unsent message list size is " + unsentMessageList.size());
                for (int i = 0; i<unsentMessageList.size(); i++) {
                    friend = null;
                    int id = unsentMessageList.get(i).message_id;
                    key = unsentMessageList.get(i).key;
                    message = unsentMessageList.get(i).message;
                    boolean sendingSucceeded = true;
                    try {
                        friend = toxSingleton.friendsList.getById(key);
                    } catch (Exception e) {
                        Log.d(TAG, e.toString());
                    }
                    try {
                        if (friend != null) {
                            Log.d(TAG, "Sending message to " + friend.getName());
                            toxSingleton.jTox.sendMessage(friend, message, id);
                        }
                    } catch (ToxException e) {
                        Log.d(TAG, e.toString());
                        e.printStackTrace();
                        sendingSucceeded = false;
                    }
                    if (sendingSucceeded) {
                        db.updateUnsentMessage(id);
                    }
                }
                db.close();
                if (toxSingleton.activeFriendKey != null) {
            /* Broadcast to update UI */
                    notify = new Intent(Constants.BROADCAST_ACTION);
                    notify.putExtra("action", Constants.UPDATE_MESSAGES);
                    notify.putExtra("key", toxSingleton.activeFriendKey);
                    LocalBroadcastManager.getInstance(this).sendBroadcast(notify);
                }
                break;

            case Constants.SEND_MESSAGE:
                Log.d(TAG, "Constants.SEND_MESSAGE");
                key = intent.getStringExtra("key");
                message = intent.getStringExtra("message");
            /* Send message */
                friend = null;
                Random generator = new Random();
                int id = generator.nextInt();
                boolean sendingSucceeded = true;
                try {
                    friend = toxSingleton.friendsList.getById(key);
                } catch (Exception e) {
                    Log.d(TAG, e.toString());
                }
                try {
                    if (friend != null) {
                        Log.d(TAG, "Sending message to " + friend.getName());
                        toxSingleton.jTox.sendMessage(friend, message, id);
                    }
                } catch (ToxException e) {
                    Log.d(TAG, e.toString());
                    e.printStackTrace();
                    sendingSucceeded = false;
                }
                db = new AntoxDB(getApplicationContext());
                if (sendingSucceeded) {
            /* Add message to chatlog */
                    db.addMessage(id, key, message, true, false, false, true);
                    db.close();
            /* Broadcast to update UI */
                    notify = new Intent(Constants.BROADCAST_ACTION);
                    notify.putExtra("action", Constants.UPDATE_MESSAGES);
                    notify.putExtra("key", key);
                    LocalBroadcastManager.getInstance(this).sendBroadcast(notify);
                } else {
                /* Add message to chatlog */
                    db.addMessage(id, key, message, true, false, false, false);
                    db.close();
            /* Broadcast to update UI */
                    notify = new Intent(Constants.BROADCAST_ACTION);
                    notify.putExtra("action", Constants.UPDATE_MESSAGES);
                    notify.putExtra("key", key);
                    LocalBroadcastManager.getInstance(this).sendBroadcast(notify);
                }
                break;

            case Constants.FRIEND_REQUEST:
                Log.d(TAG, "Constants.FRIEND_REQUEST");
                key = intent.getStringExtra(AntoxOnFriendRequestCallback.FRIEND_KEY);
                message = intent.getStringExtra(AntoxOnFriendRequestCallback.FRIEND_MESSAGE);
            /* Add friend request to arraylist */
                toxSingleton.friend_requests.add(new FriendRequest((String) key, (String) message));
            /* Add friend request to database */
                db = new AntoxDB(getApplicationContext());
                if(!db.isFriendBlocked(key))
                    db.addFriendRequest(key, message);
                db.close();

            /* Notification */
                if(!toxSingleton.leftPaneActive) {
                    NotificationCompat.Builder mBuilder =
                            new NotificationCompat.Builder(this)
                                    .setSmallIcon(R.drawable.ic_actionbar)
                                    .setContentTitle(getString(R.string.friend_request))
                                    .setContentText(message)
                                    .setDefaults(Notification.DEFAULT_ALL).setAutoCancel(true);

                    int ID = toxSingleton.friend_requests.size();
                    Intent targetIntent = new Intent(getApplicationContext(), MainActivity.class);
                    PendingIntent contentIntent = PendingIntent.getActivity(getApplicationContext(), 0, targetIntent, PendingIntent.FLAG_UPDATE_CURRENT);
                    mBuilder.setContentIntent(contentIntent);
                    toxSingleton.mNotificationManager.notify(ID, mBuilder.build());
                }

            /* Update friends list */
                Intent update = new Intent(Constants.BROADCAST_ACTION);
                update.putExtra("action", Constants.UPDATE);
                LocalBroadcastManager.getInstance(this).sendBroadcast(update);
                break;

            case Constants.REJECT_FRIEND_REQUEST:
                key = intent.getStringExtra("key");
                if (toxSingleton.friend_requests.size() != 0) {
                    for (int j = 0; j < toxSingleton.friend_requests.size(); j++) {
                        for (int i = 0; i < toxSingleton.friend_requests.size(); i++) {
                            if (key.equalsIgnoreCase(toxSingleton.friend_requests.get(i).requestKey)) {
                                toxSingleton.friend_requests.remove(i);
                                break;
                            }
                        }
                    }
                }
            /* Broadcast */
                notify = new Intent(Constants.BROADCAST_ACTION);
                notify.putExtra("action", Constants.REJECT_FRIEND_REQUEST);
                notify.putExtra("key", key);
                LocalBroadcastManager.getInstance(this).sendBroadcast(notify);
                break;

            case Constants.DELIVERY_RECEIPT:
                int receipt = intent.getIntExtra("receipt", -2);
                db = new AntoxDB(getApplicationContext());
                key = db.setMessageReceived(receipt);
                db.close();
                Log.d("DELIVERY RECEIPT FOR KEY: ", key);
            /* Broadcast */
                notify = new Intent(Constants.BROADCAST_ACTION);
                notify.putExtra("action", Constants.UPDATE_MESSAGES);
                notify.putExtra("key", key);
                LocalBroadcastManager.getInstance(this).sendBroadcast(notify);
                break;

            case Constants.ACCEPT_FRIEND_REQUEST:
                key = intent.getStringExtra("key");
                try {
                    toxSingleton.jTox.confirmRequest(key);

                /* Add friend to tox friends list */
                    //This is so wasteful. Should pass the info in the intent with the key
                    db = new AntoxDB(getApplicationContext());
                    ArrayList<Friend> friends = db.getFriendList(Constants.OPTION_ALL_FRIENDS);
                    //Long statement but just getting size of friends list and adding one for the friend number
                    friend = toxSingleton.friendsList.addFriend(toxSingleton.friendsList.all().size()+1);
                    int pos = -1;
                    for(int i = 0; i < friends.size(); i++) {
                        if(friends.get(i).friendKey.equals(key)) {
                            pos = i;
                            break;
                        }
                    }
                    if(pos != -1) {
                        friend.setId(key);
                        friend.setName(friends.get(pos).friendName);
                        friend.setStatusMessage(friends.get(pos).personalNote);
                    }

                    toxSingleton.jTox.save();
                    Log.d(TAG, "Saving request");

                    Log.d(TAG, "Tox friend list updated. New size: " + toxSingleton.friendsList.all().size());


                } catch (Exception e) {

                }

                if (toxSingleton.friend_requests.size() != 0) {

                    for (int i = 0; i < toxSingleton.friend_requests.size(); i++) {
                        if (key.equalsIgnoreCase(toxSingleton.friend_requests.get(i).requestKey)) {
                            toxSingleton.friend_requests.remove(i);
                            break;
                        }
                    }

                    db = new AntoxDB(getApplicationContext());
                    db.deleteFriendRequest(key);
                    db.close();

                /* Broadcast */
                    notify = new Intent(Constants.BROADCAST_ACTION);
                    notify.putExtra("action", Constants.ACCEPT_FRIEND_REQUEST);
                    notify.putExtra("key", key);
                    LocalBroadcastManager.getInstance(this).sendBroadcast(notify);
                }
                break;
            default:
                break;
        }
    }

}
