package org.lecharpentier.plugins.maven;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.scm.ChangeSet;
import org.apache.maven.scm.ScmBranch;
import org.apache.maven.scm.ScmException;
import org.apache.maven.scm.ScmFileSet;
import org.apache.maven.scm.ScmRevision;
import org.apache.maven.scm.ScmTag;
import org.apache.maven.scm.ScmVersion;
import org.apache.maven.scm.command.changelog.ChangeLogScmResult;
import org.apache.maven.scm.manager.ScmManager;
import org.apache.maven.scm.repository.ScmRepository;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.StringReader;

/**
 * Generate a release note based on the SCM configuration
 *
 * @goal generate
 */
public class NoteGeneratorMojo extends AbstractMojo {

    /**
     * @parameter expression="${outputFilename}" default-value="${project.build.directory}/release-note.txt"
     * @required
     */
    private String outputFilename;

    /**
     * @parameter expression="${project.scm.connection}"
     * @required
     */
    private String scmConnectionUrl;

    /**
     * @parameter expression="${previousVersion}"
     * @required
     */
    private String previousVersion;

    /**
     * @parameter expression="${previousVersionType}"
     * @required
     */
    private String previousVersionType;

    /**
     * @parameter expression="${currentVersion}"
     * @required
     */
    private String currentVersion;

    /**
     * @parameter expression="${currentVersionType}"
     * @required
     */
    private String currentVersionType;

    /**
     * @parameter expression="${logLineLimit}" default-value=80
     */
    private int logLineLimit;
    /**
     * @component
     */
    private ScmManager scmManager;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        File outputFile = new File(outputFilename);
        if (outputFile.exists()) {
            outputFile.delete();
        }

        try {
            if (!outputFile.getParentFile().exists()) {
                outputFile.getParentFile().mkdirs();
            }
            outputFile.createNewFile();
            OutputStream out = new FileOutputStream(outputFile);
            try {
                generateNote(out);
            } finally {
                out.close();
            }
        } catch (IOException e) {
            throw new MojoExecutionException("Error with output file", e);
        }
    }

    private void generateNote(OutputStream out) throws MojoExecutionException, MojoFailureException, IOException {
        OutputStreamWriter writer = new OutputStreamWriter(out);
        try {
            writer.append("Release note for " + currentVersion + ": \n");
            ScmVersion current = makeCurrentVersion();
            ScmVersion past = makePastVersion();
            ScmRepository repository = makeRepository();

            ChangeLogScmResult result = scmManager.changeLog(
                    repository,
                    new ScmFileSet(new File(".")),
                    past,
                    current);

            if (result.getChangeLog() != null) {
                for (ChangeSet cs : result.getChangeLog().getChangeSets()) {
                    writer.append("  " + getCommentSumUp(cs.getComment()) + "\n");
                }
            } else {
                writer.append("  Nothing to say..");
            }
        } catch (ScmException e) {
            throw new MojoFailureException("Problem with scm");
        } finally {
            writer.close();
        }
    }

    private ScmRepository makeRepository() throws ScmException {
        return scmManager.makeScmRepository(scmConnectionUrl);
    }

    private ScmVersion makeCurrentVersion() throws MojoExecutionException {
        if ("tag".equals(currentVersionType.toLowerCase())) {
            return new ScmTag(currentVersion);
        }
        if ("revision".equals(currentVersionType.toLowerCase())) {
            return new ScmRevision(currentVersion);
        }
        if ("branch".equals(currentVersionType.toLowerCase())) {
            return new ScmBranch(currentVersion);
        }
        throw new MojoExecutionException("CurrentVersionType not recognized, only tag, branch and revision");
    }

    private ScmVersion makePastVersion() throws MojoExecutionException {
        if ("tag".equals(previousVersionType.toLowerCase())) {
            return new ScmTag(previousVersion);
        }
        if ("revision".equals(previousVersionType.toLowerCase())) {
            return new ScmRevision(previousVersion);
        }
        if ("branch".equals(previousVersionType.toLowerCase())) {
            return new ScmBranch(previousVersion);
        }
        throw new MojoExecutionException("PreviousVersionType not recognized, only tag, branch and revision");
    }

    private String getCommentSumUp(String comment) {
        StringBuilder sb = new StringBuilder();
        StringReader sr = null;
        try {
            int c, index = 0;
            sr = new StringReader(comment);
            while ((c = sr.read()) != -1 && index < logLineLimit) {
                if ((char) c == '\n') {
                    break;
                }
                sb.append((char) c);
                index++;
            }
            if (index == logLineLimit - 1) {
                sb.replace(sb.length() - 3, sb.length(), "...");
            }
        } finally {
            if (sr != null)
                sr.close();
            return sb.toString();
        }
    }
}
