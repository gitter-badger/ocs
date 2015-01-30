package edu.gemini.itc.operation;

import edu.gemini.itc.parameters.TeleParameters;
import edu.gemini.itc.shared.*;

/**
 * The TelescopeBackgroundVisitor class is designed to adjust the SED for the
 * background given off by the telescope.
 */
public class TelescopeBackgroundVisitor implements SampledSpectrumVisitor {

    private final ArraySpectrum telescopeBack;
    private final String setup;

    /**
     * Constructs TelescopeBackgroundVisitor with specified port and coating.
     * We will use a different background file for different
     * ports and coatings.
     */
    public TelescopeBackgroundVisitor(final TeleParameters tp, final String site, final String wavelenRange) throws Exception {

        final String _fullBackgroundResource;
        if (!wavelenRange.equals(ITCConstants.VISIBLE)) {

            final String filenameBase = "/HI-Res/" + site + wavelenRange + ITCConstants.TELESCOPE_BACKGROUND_LIB + "/" + ITCConstants.GS_TELESCOPE_BACKGROUND_FILENAME_BASE;
            setup = getFileName(tp);
            _fullBackgroundResource = filenameBase + setup + ITCConstants.DATA_SUFFIX;

        } else {

            final String filenameBase = ITCConstants.TELESCOPE_BACKGROUND_FILENAME_BASE;
            setup = getFileName(tp) + "_ph";
            _fullBackgroundResource = ITCConstants.TELESCOPE_BACKGROUND_LIB + "/" + filenameBase + setup + ITCConstants.DATA_SUFFIX;
        }

        telescopeBack = new DefaultArraySpectrum(_fullBackgroundResource);


    }

    /** Gets the file name for the given port and mirror coating. */
    private String getFileName(final TeleParameters tp) {
        switch (tp.getInstrumentPort()) {

            case UP_LOOKING:
                switch (tp.getMirrorCoating()) {
                    case ALUMINIUM:
                        return "_2al";
                    case SILVER:
                        return "_2ag";
                    default:
                        throw new IllegalArgumentException("unknown coating");
                }

            case SIDE_LOOKING:
                switch (tp.getMirrorCoating()) {
                    case ALUMINIUM:
                        return "_2al+1ag";
                    case SILVER:
                        return "_3ag";
                    default:
                        throw new IllegalArgumentException("unknown coating");
                }

            default:
                throw new IllegalArgumentException("unknown port");
        }
    }

    /**
     * Implements the SampledSpectrumVisitor interface
     */
    public void visit(SampledSpectrum sed) throws Exception {
        for (int i = 0; i < sed.getLength(); i++) {
            sed.setY(i, telescopeBack.getY(sed.getX(i)) + sed.getY(i));
        }
    }

    public String toString() {
        return "TelescopeBackgroundVisitor using setup " + setup;
    }
}
