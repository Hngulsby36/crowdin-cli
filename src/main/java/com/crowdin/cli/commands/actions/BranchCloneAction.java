package com.crowdin.cli.commands.actions;

import com.crowdin.cli.client.CrowdinProjectFull;
import com.crowdin.cli.client.ExistsResponseException;
import com.crowdin.cli.client.ProjectClient;
import com.crowdin.cli.commands.NewAction;
import com.crowdin.cli.commands.Outputter;
import com.crowdin.cli.properties.ProjectProperties;
import com.crowdin.cli.utils.console.ConsoleSpinner;
import com.crowdin.cli.utils.console.ExecutionStatus;
import com.crowdin.client.branches.model.BranchCloneStatus;
import com.crowdin.client.branches.model.CloneBranchRequest;
import com.crowdin.client.branches.model.ClonedBranch;
import com.crowdin.client.projectsgroups.model.Type;
import com.crowdin.client.sourcefiles.model.Branch;
import lombok.AllArgsConstructor;

import java.util.Objects;
import java.util.Optional;

import static com.crowdin.cli.BaseCli.RESOURCE_BUNDLE;

@AllArgsConstructor
class BranchCloneAction implements NewAction<ProjectProperties, ProjectClient> {

    private final String source;
    private final String target;
    private final boolean noProgress;

    @Override
    public void act(Outputter out, ProjectProperties properties, ProjectClient client) {
        CrowdinProjectFull project = ConsoleSpinner.execute(out, "message.spinner.fetching_project_info", "error.collect_project_info",
            this.noProgress, false, client::downloadFullProject);

        boolean isStringsBasedProject = Objects.equals(project.getType(), Type.STRINGS_BASED);
        if (!isStringsBasedProject) {
            throw new RuntimeException(RESOURCE_BUNDLE.getString("error.string_based_only"));
        }

        Optional<Branch> branch = project.findBranchByName(source);
        if (!branch.isPresent()) {
            throw new RuntimeException(String.format(RESOURCE_BUNDLE.getString("error.branch_not_exists"), source));
        }
        Long branchId = branch.get().getId();
        CloneBranchRequest request = new CloneBranchRequest();
        request.setName(target);
        BranchCloneStatus status = cloneBranch(out, client, branchId, request);
        ClonedBranch clonedBranch = client.getClonedBranch(branchId, status.getIdentifier());
        out.println(ExecutionStatus.OK.withIcon(String.format(RESOURCE_BUNDLE.getString("message.branch.list"), clonedBranch.getId(), target)));
    }

    private BranchCloneStatus cloneBranch(Outputter out, ProjectClient client, Long branchId, CloneBranchRequest request) {
        return ConsoleSpinner.execute(
            out,
            "message.spinner.cloning_branch",
            "error.branch.clone",
            this.noProgress,
            false,
            () -> {
                BranchCloneStatus status;
                try {
                    status = client.cloneBranch(branchId, request);
                } catch (ExistsResponseException e) {
                    throw new RuntimeException(String.format(RESOURCE_BUNDLE.getString("message.branch_already_exists"), target));
                }

                while (!status.getStatus().equalsIgnoreCase("finished")) {
                    ConsoleSpinner.update(
                        String.format(RESOURCE_BUNDLE.getString("message.spinner.cloning_branch_percents"), status.getProgress()));
                    Thread.sleep(1000);

                    status = client.checkCloneBranchStatus(branchId, status.getIdentifier());

                    if (status.getStatus().equalsIgnoreCase("failed")) {
                        throw new RuntimeException(RESOURCE_BUNDLE.getString("error.branch.clone"));
                    }
                }
                ConsoleSpinner.update(String.format(RESOURCE_BUNDLE.getString("message.spinner.cloning_branch_percents"), 100));
                return status;
            }
        );
    }
}