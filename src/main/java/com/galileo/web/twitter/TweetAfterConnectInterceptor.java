/*
 * Copyright 2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.galileo.web.twitter;

import com.galileo.web.account.PlaceRepository;
import com.galileo.web.account.Post;
import com.galileo.web.account.PostRepository;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.inject.Inject;
import org.android.json.JSONArray;
import org.springframework.social.connect.Connection;
import org.springframework.social.connect.ConnectionFactory;
import org.springframework.social.connect.web.ConnectInterceptor;
import org.springframework.social.twitter.api.StreamDeleteEvent;
import org.springframework.social.twitter.api.StreamListener;
import org.springframework.social.twitter.api.StreamWarningEvent;
import org.springframework.social.twitter.api.Tweet;
import org.springframework.social.twitter.api.Twitter;
import org.springframework.social.twitter.api.UserStreamParameters;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.WebRequest;

public class TweetAfterConnectInterceptor implements ConnectInterceptor<Twitter> {

    @Inject
    private PostRepository postRepository;

    @Inject
    private PlaceRepository placeRepository;

    @Override
    public void preConnect(ConnectionFactory<Twitter> provider, MultiValueMap<String, String> parameters, WebRequest request) {
        if (StringUtils.hasText(request.getParameter(POST_TWEET_PARAMETER))) {
            request.setAttribute(POST_TWEET_ATTRIBUTE, Boolean.TRUE, WebRequest.SCOPE_SESSION);
        }
    }

    @Override
    public void postConnect(Connection<Twitter> connection, WebRequest request) {

        final Twitter twitter = connection.getApi();
        UserStreamParameters streamParameters = new UserStreamParameters();
        streamParameters.with(UserStreamParameters.WithOptions.FOLLOWINGS);
        streamParameters.includeReplies(true);

        twitter.streamingOperations().user(streamParameters, new ArrayList<StreamListener>(Arrays.asList(
                new StreamListener() {

                    String username = twitter.userOperations().getScreenName();

                    @Override
                    public void onTweet(Tweet tweet) {
                        String place = tweet.getPlace();
                        if (place != null) {
                            try {
                                JSONArray array = new JSONArray(place);
                                array = array.getJSONArray(0);
                                array = array.getJSONArray(0);
                                double lng = array.getDouble(0);
                                double lat = array.getDouble(1);
                                System.out.println(lng + lat + "");
                                postRepository.createPost(new Post(username, tweet.getUser().getScreenName(), tweet.getText(), lng, lat, null));
                            } catch (Exception ex) {
                                Logger.getLogger(TweetAfterConnectInterceptor.class.getName()).log(Level.SEVERE, null, ex);
                            }
                        } else {
                            place = tweet.getUser().getLocation();
                            if (place != null && !place.trim().isEmpty()) {
                                double[] coord = placeRepository.findCoordForPlace(place.trim());
                                if (coord != null) {
                                    postRepository.createPost(new Post(username, tweet.getUser().getScreenName(), tweet.getText(), coord[0], coord[1], null));
                                } else {
                                    postRepository.createPost(new Post(username, tweet.getUser().getScreenName(), tweet.getText(), null, null, place));
                                }
                            }
                        }
                    }

                    @Override
                    public void onDelete(StreamDeleteEvent sde) {
                        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
                    }

                    @Override
                    public void onLimit(int i) {
                        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
                    }

                    @Override
                    public void onWarning(StreamWarningEvent swe) {
                        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
                    }
                }
        )));

        request.removeAttribute(POST_TWEET_ATTRIBUTE, WebRequest.SCOPE_SESSION);
    }

    private static final String POST_TWEET_PARAMETER = "postTweet";

    private static final String POST_TWEET_ATTRIBUTE = "twitterConnect." + POST_TWEET_PARAMETER;
}