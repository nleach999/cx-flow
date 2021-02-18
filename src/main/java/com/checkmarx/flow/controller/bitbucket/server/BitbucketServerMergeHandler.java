package com.checkmarx.flow.controller.bitbucket.server;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.checkmarx.flow.constants.FlowConstants;
import com.checkmarx.flow.dto.BugTracker;
import com.checkmarx.flow.dto.EventResponse;
import com.checkmarx.flow.dto.ScanRequest;
import com.checkmarx.flow.utils.ScanUtils;
import com.checkmarx.sdk.dto.filtering.FilterConfiguration;

import org.slf4j.Logger;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;

import lombok.NonNull;
import lombok.experimental.SuperBuilder;

@SuperBuilder
public class BitbucketServerMergeHandler extends BitbucketServerEventHandler {

    private static final Logger log = org.slf4j.LoggerFactory.getLogger(BitbucketServerMergeHandler.class);

    @NonNull
    private String currentBranch;

    @NonNull
    private String targetBranch;
    
    @NonNull
    private String fromRefLatestCommit;

    @Override
    public ResponseEntity<EventResponse> execute() {
        String uid = configProvider.getHelperService().getShortUid();
        MDC.put(FlowConstants.MAIN_MDC_ENTRY, uid);

        controllerRequest = webhookUtils.ensureNotNull(controllerRequest);

        log.info("Processing BitBucket MERGE request");

        try {
            // PullRequest pullRequest = event.getPullRequest();
            // FromRef fromRef = pullRequest.getFromRef();
            // ToRef toRef = pullRequest.getToRef();
            // Repository fromRefRepository = fromRef.getRepository();
            // Repository_ toRefRepository = toRef.getRepository();
            
            // String application = fromRefRepository.getName();
            if (!ScanUtils.empty(controllerRequest.getApplication())) {
                application = controllerRequest.getApplication();
            }

            BugTracker.Type bugType = BugTracker.Type.BITBUCKETSERVERPULL;
            if (!ScanUtils.empty(controllerRequest.getBug())) {
                bugType = ScanUtils.getBugTypeEnum(controllerRequest.getBug(), configProvider.getFlowProperties().getBugTrackerImpl());
            }
            Optional.ofNullable(controllerRequest.getAppOnly()).ifPresent(configProvider.getFlowProperties()::setTrackApplicationOnly);

            if (ScanUtils.empty(product)) {
                product = ScanRequest.Product.CX.getProduct();
            }
            ScanRequest.Product p = ScanRequest.Product.valueOf(product.toUpperCase(Locale.ROOT));
            // String currentBranch = fromRef.getDisplayId();
            // String targetBranch = toRef.getDisplayId();
            List<String> branches = webhookUtils.getBranches(controllerRequest, configProvider.getFlowProperties());
            // String fromRefLatestCommit = fromRef.getLatestCommit();

            BugTracker bt = ScanUtils.getBugTracker(controllerRequest.getAssignee(), bugType, configProvider.getJiraProperties(),
                    controllerRequest.getBug());

            FilterConfiguration filter = configProvider.getFilterFactory().getFilter(controllerRequest, configProvider.getFlowProperties());

            String gitUrl = getGitUrl(fromRefRepository);
            String gitAuthUrl = getGitAuthUrl(gitUrl);

            String repoSelfUrl = getRepoSelfUrl(toRefRepository.getProject().getKey(), toRefRepository.getSlug());

            String mergeEndpoint = repoSelfUrl.concat(MERGE_COMMENT);
            mergeEndpoint = mergeEndpoint.replace("{id}", pullRequest.getId().toString());

            String buildStatusEndpoint = properties.getUrl().concat(BUILD_API_PATH);
            buildStatusEndpoint = buildStatusEndpoint.replace("{commit}", fromRefLatestCommit);

            String blockerCommentUrl = repoSelfUrl.concat(BLOCKER_COMMENT);
            blockerCommentUrl = blockerCommentUrl.replace("{id}", pullRequest.getId().toString());

            ScanRequest request = ScanRequest.builder().application(application).product(p)
                    .project(controllerRequest.getProject())
                    .team(controllerRequest.getTeam())
                    .namespace(getNamespace(fromRefRepository))
                    .repoName(fromRefRepository.getName())
                    .repoUrl(gitUrl)
                    .repoUrlWithAuth(gitAuthUrl)
                    .repoType(ScanRequest.Repository.BITBUCKETSERVER)
                    .branch(currentBranch)
                    .mergeTargetBranch(targetBranch)
                    .mergeNoteUri(mergeEndpoint)
                    .refs(fromRef.getId())
                    .email(null)
                    .incremental(controllerRequest.getIncremental())
                    .scanPreset(controllerRequest.getPreset())
                    .excludeFolders(controllerRequest.getExcludeFolders())
                    .excludeFiles(controllerRequest.getExcludeFiles())
                    .bugTracker(bt)
                    .filter(filter)
                    .hash(fromRefLatestCommit)
                    .build();

            setBrowseUrl(fromRefRepository, request);
            fillRequestWithCommonAdditionalData(request, toRefRepository, body);
            checkForConfigAsCode(request);
            request.putAdditionalMetadata("buildStatusUrl", buildStatusEndpoint);
            request.putAdditionalMetadata("cxBaseUrl", cxScannerService.getProperties().getBaseUrl());
            request.putAdditionalMetadata("blocker-comment-url", blockerCommentUrl);
            request.setId(uid);

            // only initiate scan/automation if target branch is applicable
            if (helperService.isBranch2Scan(request, branches)) {
                flowService.initiateAutomation(request);
            }
        } catch (IllegalArgumentException e) {
            return getBadRequestMessage(e, controllerRequest, product);
        }
        return webhookUtils.getSuccessMessage();
    }

}
