package com.forgeci.runner.docker;

import com.github.dockerjava.api.model.PullResponseItem;
import com.github.dockerjava.core.command.PullImageResultCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PullImageCallback extends PullImageResultCallback {

    private static final Logger log = LoggerFactory.getLogger(PullImageCallback.class);

    @Override
    public void onNext(PullResponseItem item) {
        if (item.getStatus() != null && item.getStatus().equals("Download complete")) {
            log.debug("Pull progress: {}", item.getStatus());
        }
        super.onNext(item);
    }
}