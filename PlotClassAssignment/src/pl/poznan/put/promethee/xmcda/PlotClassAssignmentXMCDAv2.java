package pl.poznan.put.promethee.xmcda;

import org.xmcda.ProgramExecutionResult;
import org.xmcda.Referenceable;
import org.xmcda.XMCDA;
import org.xmcda.converters.v2_v3.XMCDAConverter;
import org.xmcda.parsers.xml.xmcda_v2.XMCDAParser;
import pl.poznan.put.promethee.PlotClassAssignment;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.Map;

/**
 * Created by Maciej Uniejewski on 2016-12-26.
 */
public class PlotClassAssignmentXMCDAv2 {

    private static final ProgramExecutionResult executionResult = new ProgramExecutionResult();

    private PlotClassAssignmentXMCDAv2() {

    }

    /**
     * Loads, converts and inserts the content of the XMCDA v2 {@code file} into {@code xmcdaV3}.
     * Updates {@link #executionResult} if an error occurs.
     *
     * @param xmcdaV3     the object into which the content of {@file} is inserted
     * @param file         the XMCDA v2 file to be loaded
     * @param isMandatory   information if file is mandatory
     * @param marker       the marker to use, see {@link Referenceable.DefaultCreationObserver#currentMarker}
     * @param v2TagsOnly the list of XMCDA v2 tags to be loaded
     */
    private static void convertToV3AndMark(org.xmcda.XMCDA xmcdaV3, File file, boolean isMandatory, String marker,
                                           String... v2TagsOnly) {
        final org.xmcda.v2.XMCDA xmcdaV2 = new org.xmcda.v2.XMCDA();
        Referenceable.DefaultCreationObserver.currentMarker = marker;
        Utils.loadXMCDAv2(xmcdaV2, file, isMandatory, executionResult, v2TagsOnly);
        try {
            XMCDAConverter.convertTo_v3(xmcdaV2, xmcdaV3);
        } catch (Exception e) {
            executionResult.addError(Utils.getMessage("Could not convert " + file.getPath() + " to XMCDA v3, reason: ", e));
        }
    }

    private static void readFiles(XMCDA xmcda, String indir) {
        convertToV3AndMark(xmcda, new File(indir, "alternatives.xml"), true, "alternatives", "alternatives");
        convertToV3AndMark(xmcda, new File(indir, "categories.xml"), true, "categories", "categories");
        convertToV3AndMark(xmcda, new File(indir, "categories.xml"), true, "categoriesValues", "categoriesValues");
        convertToV3AndMark(xmcda, new File(indir, "assignments.xml"), true, "alternativesAssignments", "alternativesAffectations");
    }

/*    private static void handleResults(String outdir, Map<String, XMCDA> resultsMap) {
        org.xmcda.v2.XMCDA resultsV2;
        for ( Map.Entry<String, XMCDA> outputNameEntry : resultsMap.entrySet() )
        {
            File outputFile = new File(outdir, String.format("%s.xml", outputNameEntry.getKey()));
            try
            {
                resultsV2 = XMCDAConverter.convertTo_v2(outputNameEntry.getValue());
                if ( resultsV2 == null )
                    throw new IllegalStateException("Conversion from v3 to v2 returned a null value");
            }
            catch (Exception e)
            {
                final String err = String.format("Could not convert %s into XMCDA_v2, reason: ", outputNameEntry.getKey());
                executionResult.addError(Utils.getMessage(err, e));
                continue;
            }
            try
            {
                XMCDAParser.writeXMCDA(resultsV2, outputFile, OutputsHandler.xmcdaV2Tag(outputNameEntry.getKey()));
            }
            catch (Exception e)
            {
                final String err = String.format("Error while writing %s.xml, reason: ", outputNameEntry.getKey());
                executionResult.addError(Utils.getMessage(err, e));
                outputFile.delete();
            }
        }
    }*/

    public static void main(String[] args) throws Utils.InvalidCommandLineException {
        final Utils.Arguments params = Utils.parseCmdLineArguments(args);
        final String indir = params.inputDirectory;
        final String outdir = params.outputDirectory;
        final File prgExecResultsFile = new File(outdir, "messages.xml");

        final org.xmcda.XMCDA xmcda = new org.xmcda.XMCDA();

        readFiles(xmcda, indir);

        if ( ! (executionResult.isOk() || executionResult.isWarning() ) )
        {
            Utils.writeProgramExecutionResultsAndExit(prgExecResultsFile, executionResult, Utils.XMCDA_VERSION.v2);
        }

        final InputsHandler.Inputs inputs = InputsHandler.checkAndExtractInputs(xmcda, executionResult);
        if ( ! ( executionResult.isOk() || executionResult.isWarning() ) || inputs == null )
        {
            Utils.writeProgramExecutionResultsAndExit(prgExecResultsFile, executionResult, Utils.XMCDA_VERSION.v2);
        }
        final OutputsHandler.Output results ;
        try
        {
            results = PlotClassAssignment.execute(inputs);
        }
        catch (Exception e)
        {
            executionResult.addError(Utils.getMessage("The calculation could not be performed, reason: ", e));
            Utils.writeProgramExecutionResultsAndExit(prgExecResultsFile, executionResult, Utils.XMCDA_VERSION.v2);
            return;
        }
        try(  PrintWriter out = new PrintWriter( outdir+"/latex.txt" )  ){
            out.println( results.getLatexTable() );
        } catch (FileNotFoundException e) {
            executionResult.addError("Output file cannot be created. Reason: " + e);
        }
/*
        final Map<String, XMCDA> resultsMap = OutputsHandler.convert(results.getFirstStepAssignments(), results.getAssignments());

        handleResults(outdir, resultsMap);*/
        Utils.writeProgramExecutionResultsAndExit(prgExecResultsFile, executionResult, Utils.XMCDA_VERSION.v2);
    }
}
