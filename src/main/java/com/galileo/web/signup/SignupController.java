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
package com.galileo.web.signup;

import com.galileo.web.message.Message;
import com.galileo.web.message.MessageType;
import com.galileo.web.signin.SignInUtils;
import javax.inject.Inject;
import javax.validation.Valid;

import org.springframework.social.connect.Connection;
import org.springframework.social.connect.web.ProviderSignInUtils;
import com.galileo.web.account.Account;
import com.galileo.web.account.AccountRepository;
import com.galileo.web.account.UsernameAlreadyInUseException;
import com.galileo.web.task.TwitterMineTask;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.context.request.WebRequest;

@Controller
public class SignupController {

    private final AccountRepository accountRepository;
    private final ProviderSignInUtils providerSignInUtils = new ProviderSignInUtils();
    private final TwitterMineTask twitterMineTask;

    @Inject
    public SignupController(AccountRepository accountRepository, TwitterMineTask twitterMineTask) {
        this.accountRepository = accountRepository;
        this.twitterMineTask = twitterMineTask;
    }

    @RequestMapping(value = "/signup", method = RequestMethod.GET)
    public SignupForm signupForm(WebRequest request) {
        Connection<?> connection = providerSignInUtils.getConnectionFromSession(request);
        if (connection != null) {
            request.setAttribute("message", new Message(MessageType.INFO, "Your " + StringUtils.capitalize(connection.getKey().getProviderId()) + " account is not associated with a Spring Social Showcase account. If you're new, please sign up."), WebRequest.SCOPE_REQUEST);
            return SignupForm.fromProviderUser(connection.fetchUserProfile());
        } else {
            return new SignupForm();
        }
    }

    @RequestMapping(value = "/signup", method = RequestMethod.POST)
    public String signup(@Valid SignupForm form, BindingResult formBinding, WebRequest request) {
        if (formBinding.hasErrors()) {
            return null;
        }
        Account account = createAccount(form, formBinding);

        if (account != null) {
            SignInUtils.signin(account.getUsername());
            Connection<?> connection = providerSignInUtils.getConnectionFromSession(request);
            if (connection != null) {
                twitterMineTask.update(connection.getApi(), account.getUsername());
            }
            providerSignInUtils.doPostSignUp(account.getUsername(), request);
            return "redirect:/";
        }
        return null;
    }

    // internal helpers
    private Account createAccount(SignupForm form, BindingResult formBinding) {
        try {
            Account account = new Account(form.getUsername(), form.getPassword(), form.getFirstName(), form.getLastName());
            accountRepository.createAccount(account);
            return account;
        } catch (UsernameAlreadyInUseException e) {
            formBinding.rejectValue("username", "user.duplicateUsername", "already in use");
            return null;
        }
    }

}
