package cruz.agents;

import ddejonge.bandana.anac.ANACNegotiator;
import ddejonge.bandana.dbraneTactics.DBraneTactics;
import ddejonge.bandana.dbraneTactics.Plan;
import ddejonge.bandana.negoProtocol.*;
import ddejonge.bandana.tools.Utilities;
import ddejonge.negoServer.Message;
import es.csic.iiia.fabregues.dip.board.Phase;
import es.csic.iiia.fabregues.dip.board.Power;
import es.csic.iiia.fabregues.dip.board.Province;
import es.csic.iiia.fabregues.dip.board.Region;
import es.csic.iiia.fabregues.dip.orders.*;

import java.util.*;

@SuppressWarnings("Duplicates")

/**
 *
 */

public class OpenAINegotiator extends ANACNegotiator {

    // Necessary for base player
    public Random random = new Random();
    DBraneTactics dBraneTactics;

    /** The OpenAI Adapter that contains the necessary functions and fields to make the connection to the Open AI environment */
    OpenAIAdapterNegotiation openAIAdapter;

    /** Defines whether logs should be printed to console or not.*/
    private boolean printToConsole = true;

    /** Ordered list of regions controlled. The default list of controlled regions may not be ordered.
     * It's important for this list to be ordered, so that an action taken
     * in the same state twice leads to the same outcome.*/
    List<Region> orderedControlledRegions;

    /** Ordered list of powers. The default list of powers may not be ordered.
     * It's important for this list to be ordered, so that an action taken
     * in the same state twice leads to the same outcome.*/
    List<Power> orderedNegotiatingPowers;

    /**
     * You must implement a Constructor with exactly this signature.
     * The body of the Constructor must start with the line <code>super(args)</code>
     * but below that line you can put whatever you like.
     *
     * @param args
     */
    public OpenAINegotiator(String[] args) {
        super(args);

        dBraneTactics = this.getTacticalModule();

        // Create OpenAI Adapter
        this.openAIAdapter = new OpenAIAdapterNegotiation(this);
    }

    /**
     * Main method to start the agent.
     * <p>
     * This player can be started with the following arguments:
     * -name  	[the name of your agent]
     * -log		[the path to the folder where you want the log files to be stored]
     * -fy 		[the year after which your agent will propose a draw]
     * -gamePort  [the port of the game server]
     * -negoPort  [the port of the negotiation server]
     * <p>
     * e.g. java -jar ANACExampleNegotiator.jar -name alice -log C:\\documents\log -fy 1920 -gamePort 16713 -negoPort 16714
     * <p>
     * All of these arguments are optional.
     * <p>
     * Note however that during the competition the values of these arguments will be chosen by the organizers
     * of the competition, so you can only control them during the development of your negotiator.
     *
     * @param args
     */
    public static void main(String[] args) {
        // THE MAIN METHOD MUST CONSIST ONLY OF THE FOLLOWING TWO LINES ONCE EVERYTHING IS DONE
        // THIS IS EXPRESSED IN THE ANAC 2019 MANUAL
        OpenAINegotiator myPlayer = new OpenAINegotiator(args);
        myPlayer.run();

//        System.out.println("Hello");
//        OpenAINegotiator myPlayer = new OpenAINegotiator(args);
//        myPlayer.getDealsDataFromDipBrain();
//        System.out.println("Hello again");
    }

    /**
     * This method is automatically called at the start of the game, after the 'game' field is set.
     * <p>
     * It is called when the first NOW message is received from the game server.
     * The NOW message contains the current phase and the positions of all the units.
     * <p>
     * You are allowed, but not required, to implement this method
     */
    @Override
    public void start() {

        //You can use the logger to write stuff to the log file.
        //The location of the log file can be set through the command line option -log.
        // it is not necessary to call getLogger().enable() because this is already automatically done by the ANACNegotiator class.

        //if set to true the text will be written to file, as well as printed to the standard output stream. If set to false it will only be written to file.
        this.getLogger().logln("Game is starting!", this.printToConsole);

        // Tell the adapter a new game is starting
        this.openAIAdapter.beginningOfGame();
    }

    @Override
    public void negotiate(long negotiationDeadline) {

        this.getLogger().logln(me.getName() + ".negotiate() Negotiation BEGINNING. Duration: " + (negotiationDeadline - System.currentTimeMillis()), this.printToConsole);

        boolean alreadyProposed = false;

        // This loop repeats 2 steps. The first step is to handle any incoming messages,
        // while the second step tries to find deals to propose to the other negotiators.
        // Generally negotiation lasts 3 seconds.
        while (System.currentTimeMillis() < negotiationDeadline) {

            // I (the player) am responsible for sending/accepting new deals during this time period


            //STEP 1: Handle incoming messages.


            //See if we have received any message from any of the other negotiators.
            // e.g. a new proposal or an acceptance of a proposal made earlier.
            while (hasMessage()) {

                // Warning: you may want to add some extra code to break out of this loop,
                // just in case the other agents send so many proposals that your agent can't get
                // the chance to make any proposals itself.

                // If we haven't proposed a deal yet, ignore messages and propose deal
                if (!alreadyProposed) {
                    break;
                }

                // If yes, remove it from the message queue.
                Message receivedMessage = removeMessageFromQueue();

                if (receivedMessage.getPerformative().equals(DiplomacyNegoClient.ACCEPT)) {

                    DiplomacyProposal acceptedProposal = (DiplomacyProposal) receivedMessage.getContent();

                    // this.getLogger().logln(me.getName() + ".negotiate() Received acceptance from " + receivedMessage.getSender() + ": " + acceptedProposal, this.printToConsole);

                    // Here we can handle any incoming acceptances.
                    // This random negotiator doesn't do anything with such messages however.

                    // Note: if a certain proposal has been accepted by all players it is still not considered
                    // officially binding until the protocol manager has sent a CONFIRM message.

                    // Note: if all agents involved in a proposal have accepted the proposal, then you will not receive an ACCEPT
                    // message from the last agent that accepted it. Instead, you will directly receive a CONFIRM message from the
                    // Protocol Manager.

                } else if (receivedMessage.getPerformative().equals(DiplomacyNegoClient.PROPOSE)) {

                    DiplomacyProposal receivedProposal = (DiplomacyProposal) receivedMessage.getContent();

                    // this.getLogger().logln(me.getName() + ".negotiate() Received proposal: " + receivedProposal, this.printToConsole);

                    BasicDeal deal = (BasicDeal) receivedProposal.getProposedDeal();

                    boolean outDated = false;

                    for (DMZ dmz : deal.getDemilitarizedZones()) {

                        // Sometimes we may receive messages too late, so we check if the proposal does not
                        // refer to some round of the game that has already passed.
                        if (isHistory(dmz.getPhase(), dmz.getYear())) {
                            outDated = true;
                            break;
                        }

                        //TODO: decide whether this DMZ is acceptable or not (in combination with the rest of the proposed deal).
						/*
						List<Power> powers = dmz.getPowers();
						List<Province> provinces = dmz.getProvinces();
						*/

                    }
                    for (OrderCommitment orderCommitment : deal.getOrderCommitments()) {


                        // Sometimes we may receive messages too late, so we check if the proposal does not
                        // refer to some round of the game that has already passed.
                        if (isHistory(orderCommitment.getPhase(), orderCommitment.getYear())) {
                            outDated = true;
                            break;
                        }

                        //TODO: decide whether this order commitment is acceptable or not (in combination with the rest of the proposed deal).
                        /*Order order = orderCommitment.getOrder();*/
                    }

                    //If the deal is not outdated, then check that it is consistent with the deals we are already committed to.
                    String consistencyReport = null;
                    if (!outDated) {

                        List<BasicDeal> commitments = new ArrayList<BasicDeal>();
                        commitments.addAll(this.getConfirmedDeals());
                        commitments.add(deal);
                        consistencyReport = Utilities.testConsistency(game, commitments);


                    }

                    if (!outDated && consistencyReport == null) {
                        // DECIDE WHETHER OR NOT TO ACCEPT THE DEAL

                        // In this scenario, use hypothetical number of SCs with new deal to determine whether we should accept it or not
                        if(this.acceptProposal(receivedMessage)) {
                            this.acceptProposal(receivedProposal.getId());
                            this.getLogger().logln(me.getName() + ".negotiate()  Accepting: " + receivedProposal, this.printToConsole);
                        }

                        // In this simple scenario, we always reject incoming deals
                        // this.rejectProposal(receivedProposal.getId());
                        // this.getLogger().logln(me.getName() + ".negotiate()  Rejecting: " + receivedProposal, this.printToConsole);

                        // In this scenario, simply flip a coin to determine whether to accept the proposal or not.
                        // if (random.nextInt(2) == 0) { // accept with 50% probability.
                        //     this.acceptProposal(receivedProposal.getId());
                        //     this.getLogger().logln(me.getName() + ".negotiate()  Accepting: " + receivedProposal, this.printToConsole);
                        // }

                    }

                } else if (receivedMessage.getPerformative().equals(DiplomacyNegoClient.CONFIRM)) {

                    // The protocol manager confirms that a certain proposal has been accepted by all players involved in it.
                    // From now on we consider the deal as a binding agreement.

                    DiplomacyProposal confirmedProposal = (DiplomacyProposal) receivedMessage.getContent();

                    this.getLogger().logln(me.getName() + ".negotiate() RECEIVED CONFIRMATION OF: " + confirmedProposal, this.printToConsole);

                    BasicDeal confirmedDeal = (BasicDeal) confirmedProposal.getProposedDeal();


                    // Reject any proposal that has not yet been confirmed and that is inconsistent with the confirmed deal.
                    // NOTE that normally this is not really necessary because the Notary will already check that
                    // any deal is consistent with earlier confirmed deals before it becomes confirmed.
                    List<BasicDeal> deals = new ArrayList<BasicDeal>(2);
                    deals.add(confirmedDeal);
                    for (DiplomacyProposal standingProposal : this.getUnconfirmedProposals()) {

                        //add this proposal to the list of deals.
                        deals.add((BasicDeal) standingProposal.getProposedDeal());

                        if (Utilities.testConsistency(game, deals) != null) {
                            this.rejectProposal(standingProposal.getId());
                        }

                        //remove the deal again from the list, so that we can add the next standing deal to the list in the next iteration.
                        deals.remove(1);
                    }

                    // JC: When deals are created, their ID is "<name of power><number of deals already proposed>"
                    // If the ID contains our name, then it was proposed by us
                    boolean proposedByMe = confirmedProposal.getId().toLowerCase().contains(this.me.getName().toLowerCase());

                    // JC: If our only deal this round was accepted and confirmed, trigger accepted deal. If not, we will consider that
                    // the deal was not accepted.
                    if (proposedByMe) {
                        this.getLogger().logln(me.getName() + ".negotiate() OUR deal with ID: " + confirmedProposal.getId() + " has been confirmed", this.printToConsole);
                        this.openAIAdapter.dealsAccepted++;
                    } else {
                        // do nothing, because we already assume the deal was not accepted
                    }

                } else if (receivedMessage.getPerformative().equals(DiplomacyNegoClient.REJECT)) {

                    DiplomacyProposal rejectedProposal = (DiplomacyProposal) receivedMessage.getContent();

                    // Some player has rejected a certain proposal.
                    // This example agent doesn't do anything with such messages however.

                    // If a player first accepts a proposal and then rejects the same proposal the reject message cancels
                    // his earlier accept proposal.
                    // However, this is not true if the reject message is sent after the Notary has already sent a confirm
                    // message for that proposal. Once a proposal is confirmed it cannot be undone anymore.

                } else {

                    //We have received any other kind of message.
                    this.getLogger().logln("Received a message of unhandled type: " + receivedMessage.getPerformative() + ". Message content: " + receivedMessage.getContent().toString(), this.printToConsole);
                }
            }

            //STEP 2:  try to find a proposal to make, and if we do find one, propose it.
            if (!alreadyProposed) { //we only make proposals once per round, so we skip this if we have already proposed something.

                this.getLogger().logln(me.getName() + ".negotiate() Getting deals from DipQBrain...", this.printToConsole);

                // JC: It is here that the OpenAI module is called to generate a new deal
                ProtoMessage.DealData dealsToProposeData = this.openAIAdapter.getDealsDataFromDipBrain();
                List<BasicDeal> dealsToPropose = this.generateDeals(dealsToProposeData);

                // JC: If the Python module does not return anything or connection could not be made, use the default function to find deals
                if (dealsToPropose == null) {
                    this.getLogger().logln(me.getName() + ".negotiate() No deal was received from DipQ. Proceeding with default deal proposal.", this.printToConsole);
                    BasicDeal backupDeal = searchForNewDealToPropose();

                    if(backupDeal != null) {
                        this.proposeDeal(backupDeal);
                        this.getLogger().logln(me.getName() + ".negotiate() Proposing: " + backupDeal, this.printToConsole);
                    }
                    else {
                        this.getLogger().logln(me.getName() + ".negotiate() Could not find any deal to propose.", this.printToConsole);
                    }

                }
                else {
                    if(dealsToPropose.size() == 0) {
                        this.getLogger().logln(me.getName() + ".negotiate() We are not proposing deals this round.");
                    }
                    else {
                        for (BasicDeal deal : dealsToPropose) {
                            this.proposeDeal(deal);
                            this.getLogger().logln(me.getName() + ".negotiate() Proposing: " + deal, this.printToConsole);
                        }
                    }
                }

                alreadyProposed = true;
            }

            // Wait before next cycle (commented because negotiation only lasts 100ms on my custom configuration)
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
            }
        }


        //whenever you like, you can also propose a draw to all other surviving players:
        //this.proposeDraw();

        this.getLogger().logln(me.getName() + ".negotiate() Negotiation ENDING. Deadline minus current time: " + (negotiationDeadline - System.currentTimeMillis()), this.printToConsole);
    }

    /**
     * According to the data received from DipBrainANAC DRL module, decide what deals should be proposed.
     * @param dealData
     * @return
     */
    private List<BasicDeal> generateDeals(ProtoMessage.DealData dealData) {
        List<BasicDeal> deals = new ArrayList<>();

        // Get current controlled regions and then create an ordered list with them to be use in deal generation
        this.orderedControlledRegions = this.sortRegionList(this.me.getControlledRegions());

        // Get the current negotiating powers and then create an ordered list for deal generation without us in the list
        this.orderedNegotiatingPowers = this.sortPowerList(this.getNegotiatingPowers());
        this.orderedNegotiatingPowers.remove(this.me);

        // Derive year and phase of deal from number of phases ahead
        Map.Entry<Integer, Phase> phaseAndYear = cruz.agents.Utilities.calculatePhaseAndYear(this.game.getYear(), this.game.getPhase(), dealData.getPhasesFromNow());

        int year = phaseAndYear.getKey();
        Phase phase = phaseAndYear.getValue();

        // Only if execute is true
        if(dealData.getDefendUnit().getExecute()) {
            int clippedRegionIndex = this.clipRegionIndex(dealData.getDefendUnit().getRegion());
            BasicDeal generatedDeal = generateDefendUnitsMutual(clippedRegionIndex, year, phase);

            if (generatedDeal != null) {
                // if we could find a deal
                deals.add(generatedDeal);
            }
        }

        // Only if execute is true
        if(dealData.getDefendSC().getExecute()) {
            int clippedPowerIndex = this.clipPowerIndex(dealData.getDefendSC().getAllyPower());
            deals.add(generateDefendSupplyCentersMutual(clippedPowerIndex, year, phase));
        }

        // Only if execute is true
        if(dealData.getAttackRegion().getExecute()) {
            int clippedRegionIndex = this.clipRegionIndex(dealData.getAttackRegion().getRegion());
            BasicDeal generatedDeal = generateAttack(clippedRegionIndex, year, phase);

            if (generatedDeal != null) {
                // if we could find a deal
                deals.add(generatedDeal);
            }
        }

        // Only if execute is true
        if(dealData.getSupportAttackRegion().getExecute()) {
            int clippedRegionIndex = this.clipRegionIndex(dealData.getSupportAttackRegion().getRegion());
            BasicDeal generatedDeal = generateSupportAttack(clippedRegionIndex, year, phase);

            if (generatedDeal != null) {
                // if we could find a deal
                deals.add(generatedDeal);
            }
        }

        return deals;
    }

    BasicDeal searchForNewDealToPropose() {

        BasicDeal bestDeal = null;
        Plan bestPlan = null;

        //Get a copy of our list of current commitments.
        List<BasicDeal> commitments = this.getConfirmedDeals();

        //First, let's see what happens if we do not make any new commitments.
        bestPlan = this.dBraneTactics.determineBestPlan(game, me, commitments);

        //If our current commitments are already inconsistent then we certainly
        // shouldn't make any more commitments.
        if (bestPlan == null) {
            return null;
        }

        //let's generate 10 random deals and pick the best one.
        for (int i = 0; i < 10; i++) {

            //generate a random deal.
            BasicDeal randomDeal = generateRandomDeal();

            if (randomDeal == null) {
                continue;
            }


            //add it to the list containing our existing commitments so that dBraneTactics can determine a plan.
            commitments.add(randomDeal);


            //Ask the D-Brane Tactical Module what it would do under these commitments.
            Plan plan = this.dBraneTactics.determineBestPlan(game, me, commitments);

            //Check if the returned plan is better than the best plan found so far.
            if (plan != null && plan.getValue() > bestPlan.getValue()) {
                bestPlan = plan;
                bestDeal = randomDeal;
            }


            //Remove the randomDeal from the list, for the next iteration.
            commitments.remove(commitments.size() - 1);

            //NOTE: the value returned by plan.getValue() represents the number of Supply Centers that the D-Brane Tactical Module
            // expects to conquer in the current round under the given commitments.
            //
            // Of course, this is only a rough indication of which plan is truly the "best". After all, sometimes it is better
            // not to try to conquer as many Supply Centers as you can directly, but rather organize your armies and only attack in a later
            // stage.
            // Therefore, you may want to implement your own algorithm to determine which plan is the best.
            // You can call plan.getMyOrders() to retrieve the complete list of orders that D-Brane has chosen for you under the given commitments.

        }


        return bestDeal;


    }


    public BasicDeal generateRandomDeal() {


        //Get the names of all the powers that are connected to the negotiation server and which have not been eliminated.
        List<Power> aliveNegotiatingPowers = this.getNegotiatingPowers();

        //if there are less than 2 negotiating powers left alive (only me), then it makes no sense to negotiate.
        int numAliveNegoPowers = aliveNegotiatingPowers.size();
        if (numAliveNegoPowers < 2) {
            return null;
        }

        //Let's generate 3 random demilitarized zones.
        List<DMZ> demilitarizedZones = new ArrayList<DMZ>(3);
        for (int i = 0; i < 3; i++) {

            //1. Create a list of powers
            ArrayList<Power> powers = new ArrayList<Power>(2);

            //1a. add myself to the list
            powers.add(me);

            //1b. add a random other power to the list.
            Power randomPower = me;
            while (randomPower.equals(me)) {

                int numNegoPowers = aliveNegotiatingPowers.size();
                randomPower = aliveNegotiatingPowers.get(random.nextInt(numNegoPowers));
            }
            powers.add(randomPower);

            //2. Create a list containing 3 random provinces.
            ArrayList<Province> provinces = new ArrayList<Province>();
            for (int j = 0; j < 3; j++) {
                int numProvinces = this.game.getProvinces().size();
                Province randomProvince = this.game.getProvinces().get(random.nextInt(numProvinces));
                provinces.add(randomProvince);
            }


            //This agent only generates deals for the current year and phase.
            // However, you can pick any year and phase here, as long as they do not lie in the past.
            // (actually, you can also propose deals for rounds in the past, but it doesn't make any sense
            //  since you obviously cannot obey such deals).
            demilitarizedZones.add(new DMZ(game.getYear(), game.getPhase(), powers, provinces));

        }


        //let's generate 3 random OrderCommitments
        List<OrderCommitment> randomOrderCommitments = new ArrayList<OrderCommitment>();


        //get all units of the negotiating powers.
        List<Region> units = new ArrayList<Region>();
        for (Power power : aliveNegotiatingPowers) {
            units.addAll(power.getControlledRegions());
        }


        for (int i = 0; i < 3; i++) {

            //Pick a random unit and remove it from the list
            if (units.size() == 0) {
                break;
            }
            Region randomUnit = units.remove(random.nextInt(units.size()));

            //Get the corresponding power
            Power power = game.getController(randomUnit);

            //Determine a list of potential destinations for the unit.
            // a Region is a potential destination for a unit if it is adjacent to that unit (or it is the current location of the unit)
            //  and the Province is not demilitarized for the Power controlling that unit.
            List<Region> potentialDestinations = new ArrayList<Region>();

            //Create a list of adjacent regions, including the current location of the unit.
            List<Region> adjacentRegions = new ArrayList<>(randomUnit.getAdjacentRegions());
            adjacentRegions.add(randomUnit);

            for (Region adjacentRegion : adjacentRegions) {

                Province adjacentProvince = adjacentRegion.getProvince();

                //Check that the adjacent Region is not demilitarized for the power controlling the unit.
                boolean isDemilitarized = false;
                for (DMZ dmz : demilitarizedZones) {
                    if (dmz.getPowers().contains(power) && dmz.getProvinces().contains(adjacentProvince)) {
                        isDemilitarized = true;
                        break;
                    }

                }

                //If it is not demilitarized, then we can add the region to the list of potential destinations.
                if (!isDemilitarized) {
                    potentialDestinations.add(adjacentRegion);
                }
            }


            int numPotentialDestinations = potentialDestinations.size();
            if (numPotentialDestinations > 0) {

                Region randomDestination = potentialDestinations.get(random.nextInt(numPotentialDestinations));

                Order randomOrder;
                if (randomDestination.equals(randomUnit)) {
                    randomOrder = new HLDOrder(power, randomUnit);
                } else {
                    randomOrder = new MTOOrder(power, randomUnit, randomDestination);
                }
                // Of course we could also propose random support orders, but we don't do that here.


                //We only generate deals for the current year and phase.
                // However, you can pick any year and phase here, as long as they do not lie in the past.
                // (actually, you can also propose deals for rounds in the past, but it doesn't make any sense
                //  since you obviously cannot obey such deals).
                randomOrderCommitments.add(new OrderCommitment(game.getYear(), game.getPhase(), randomOrder));
            }

        }

        BasicDeal deal = new BasicDeal(randomOrderCommitments, demilitarizedZones);


        return deal;

    }

    /**
     * Function that determines whether a proposal should be accepted or not. If we earn more SCs with the deal than
     * without it, we accept it. If we lose, we reject it. If it doesn't affect us, we only accept it if the power
     * proposing has at least 2 SCs less than us (if it's quite weaker).
     *
     * @param receivedProposalMessage The message containing the proposal.
     * @return True we should accept the deal. False otherwise.
     */
    protected boolean acceptProposal(Message receivedProposalMessage) {

        DiplomacyProposal receivedProposal = (DiplomacyProposal) receivedProposalMessage.getContent();

        BasicDeal deal = (BasicDeal) receivedProposal.getProposedDeal();

        List<BasicDeal> currentDeals = this.getConfirmedDeals();
        List<BasicDeal> hypotheticalDeals = new ArrayList<>(currentDeals);
        hypotheticalDeals.add(deal);

        Plan basePlan = this.dBraneTactics.determineBestPlan(this.game, this.me, currentDeals);
        Plan hypotheticalPlan = this.dBraneTactics.determineBestPlan(this.game, this.me, hypotheticalDeals);

        if(hypotheticalPlan == null) {   // if for some reason plan returns null, reject new proposal
            return false;
        }

        int balanceSCs = hypotheticalPlan.getValue() - basePlan.getValue();

        if (balanceSCs > 0) {
            // If we earn some SC from new deal, then accept it
            return true;
        }
        else if (balanceSCs < 0) {
            return false;
        }
        else {
            Power proposingPower = this.game.getPower(receivedProposalMessage.getSender());

            // If power proposing is at least 2 SCs behind us, accept deal (it may reciprocate later on)
            // IF power proposing is stronger than that, reject deal
            return proposingPower.getOwnedSCs().size() <= this.me.getOwnedSCs().size() - 2;
        }
    }

    /**
     * This methods returns ONE deal, to support a hold order in ONE random region. The deal is made randomly
     * to a valid neighbour power.
     *
     * @param year
     * @param phase
     * @return
     */
    protected BasicDeal generateDefendUnitsMutual(int regionIndex, int year, Phase phase) {
        List<OrderCommitment> ocs = new ArrayList<>();
        List<DMZ> dmzs = new ArrayList<>();

        // Choose random unit

        // An unit is addressed by the Region it occupies.
        Region ourRegion = this.orderedControlledRegions.get(regionIndex);

        List<Region> adjacentRegions = ourRegion.getAdjacentRegions();

        // Shuffle in order to randomize the process
        Collections.shuffle(adjacentRegions);

        // Try to get a deal with a random surrounding region.
        for(Region adjacentRegion : adjacentRegions) {
            Power controller = this.game.getController(adjacentRegion);
            if(controller == null){
                // if no one controls region, don't consider it
                continue;
            }
            if (!this.orderedNegotiatingPowers.contains(controller)) {
                // if it's a non-negotiating or if it's us, don't consider
                continue;
            }

            HLDOrder hldOrderTheirs = new HLDOrder(controller, adjacentRegion);
            HLDOrder hldOrderOurs = new HLDOrder(this.me, ourRegion);

            SUPOrder supOrderTheirs = new SUPOrder(controller, ourRegion, hldOrderOurs);
            SUPOrder supOrderOurs = new SUPOrder(this.me, adjacentRegion, hldOrderTheirs);

            ocs.add(new OrderCommitment(year, phase, supOrderOurs));
            ocs.add(new OrderCommitment(year, phase, supOrderTheirs));

            BasicDeal deal = new BasicDeal(ocs, dmzs);
            return deal;
        }

        return null;
    }

    /**
     * This method returns ONE deal, where we choose a random Power and propose not invading (DMZ) each other's
     * supply centers.
     *
     * @param year
     * @param phase
     * @return
     */
    protected BasicDeal generateDefendSupplyCentersMutual(int powerIndex, int year, Phase phase) {
        List<OrderCommitment> ocs = new ArrayList<>();
        List<DMZ> dmzs = new ArrayList<>();

        List<Province> ourProvinces = this.me.getOwnedSCs();

        // Try to get a deal with a random Power.
        Power opponent = this.orderedNegotiatingPowers.get(powerIndex);

        List<Province> provincesDMZ = new ArrayList<>();
        provincesDMZ.addAll(opponent.getOwnedSCs());
        provincesDMZ.addAll(ourProvinces);

        List<Power> involvedPowers = new ArrayList<>();
        involvedPowers.add(this.me);
        involvedPowers.add(opponent);

        dmzs.add(new DMZ(year, phase, involvedPowers, provincesDMZ));
        return new BasicDeal(ocs, dmzs);
    }

    /**
     * This methods returns ONE deal, to coordinate an attack on ONE random region,
     * with the support of another random region.
     *
     * In this deal, WE are the main attackers (therefore we win the control).
     *
     * @param year
     * @param phase
     * @return
     */
    protected BasicDeal generateAttack(int regionIndex, int year, Phase phase) {
        List<OrderCommitment> ocs = new ArrayList<>();
        List<DMZ> dmzs = new ArrayList<>();

        // An unit is addressed by the Region it occupies.
        Region ourRegion = this.orderedControlledRegions.get(regionIndex);

        List<Region> adjacentRegions = ourRegion.getAdjacentRegions();

        // Shuffle in order to randomize the process
        Collections.shuffle(adjacentRegions);

        // Try to get a deal to attack a random surrounding region.
        for(Region targetRegion : adjacentRegions) {
            Power targetPower = this.game.getController(targetRegion);

            List<Region> possibleSupports = new ArrayList<>(adjacentRegions);
            possibleSupports.remove(targetRegion);

            MTOOrder mtoOrder = new MTOOrder(this.me, ourRegion, targetRegion);

            for(Region supportingRegion: possibleSupports) {
                Power supportingPower = this.game.getController(supportingRegion);

                if (supportingPower == null) {
                    // if the region is not controlled by anyone, find a new one that is
                    continue;
                }

                if (supportingPower.equals(targetPower)) {
                    // if the supporting power is the target, don't ask for help, obviously
                    continue;
                }

                if (!this.orderedNegotiatingPowers.contains(supportingPower)) {
                    // if the supporting power does not negotiate
                    continue;
                }

                SUPMTOOrder supmtoOrder = new SUPMTOOrder(supportingPower, supportingRegion, mtoOrder);

                ocs.add(new OrderCommitment(year, phase, mtoOrder));
                ocs.add(new OrderCommitment(year, phase, supmtoOrder));

                return new BasicDeal(ocs, dmzs);
            }
        }

        return null;
    }

    /**
     * This methods returns ONE deal, to coordinate an attack on ONE random region,
     * with the support of another random region.
     *
     * In this deal, THE OTHER OPPONENT is the main attacker (therefore they win the control).
     *
     * @param year
     * @param phase
     * @return
     */
    protected BasicDeal generateSupportAttack(int regionIndex, int year, Phase phase) {
        List<OrderCommitment> ocs = new ArrayList<>();
        List<DMZ> dmzs = new ArrayList<>();

        // An unit is addressed by the Region it occupies.
        Region ourRegion = this.orderedControlledRegions.get(regionIndex);

        List<Region> adjacentRegions = ourRegion.getAdjacentRegions();

        // Shuffle in order to randomize the process
        Collections.shuffle(adjacentRegions);

        // Try to get a deal to attack a random surrounding region.
        for(Region targetRegion : adjacentRegions) {
            Power targetPower = this.game.getController(targetRegion);

            List<Region> possibleSupports = new ArrayList<>(adjacentRegions);
            possibleSupports.remove(targetRegion);

            for(Region regionToSupport: possibleSupports) {
                Power powerToSupport = this.game.getController(regionToSupport);

                if (powerToSupport == null) {
                    // if the region is not controlled by anyone, find a new one that is
                    continue;
                }

                if (powerToSupport.equals(targetPower)) {
                    // if the supporting power is the target, don't ask for help, obviously
                    continue;
                }

                if (!this.orderedNegotiatingPowers.contains(powerToSupport)) {
                    // if the supporting power does not negotiate
                    continue;
                }

                MTOOrder mtoOrder = new MTOOrder(powerToSupport, ourRegion, targetRegion);
                SUPMTOOrder supmtoOrder = new SUPMTOOrder(this.me, regionToSupport, mtoOrder);

                ocs.add(new OrderCommitment(year, phase, mtoOrder));
                ocs.add(new OrderCommitment(year, phase, supmtoOrder));

                return new BasicDeal(ocs, dmzs);
            }
        }

        return null;
    }

    /**
     * Returns an alphabetically ordered region list, according to its name.
     * @param controlledRegions
     */
    protected List<Region> sortRegionList(List<Region> controlledRegions) {
        List<Region> orderedControlledRegions = new ArrayList<>(controlledRegions);
        orderedControlledRegions.sort(new Comparator<Region>() {
            @Override
            public int compare(Region region, Region t1) {
                return region.getName().compareToIgnoreCase(t1.getName());
            }
        });

        return orderedControlledRegions;
    }

    /**
     * Returns an alphabetically ordered region list, according to its name.
     * @param powers
     */
    protected List<Power> sortPowerList(List<Power> powers) {
        List<Power> orderedPowers = new ArrayList<>(powers);
        orderedPowers.sort(new Comparator<Power>() {
            @Override
            public int compare(Power power, Power t1) {
                return power.getName().compareToIgnoreCase(t1.getName());
            }
        });

        return orderedPowers;
    }


    /**
     * This method clips the given integer, in case it is greater than the size of the list of controlled regions.
     *
     * Because the DRL agent will provide an integer n where 0 <= n <= maximum_number_of_units, n may be larger
     * than the number of CURRENT units. Therefore, we clip it, so that any n greater than our number of units maps to
     * the maximum index possible.
     * @param n
     * @return
     */
    private int clipRegionIndex(int n) {
        if(n >= this.me.getControlledRegions().size()) {
            return this.me.getControlledRegions().size() - 1;
        }
        else {
            return n;
        }
    }

    /**
     * This method clips the given integer, in case it is greater than the size of the list of controlled SCs/provinces.
     *
     * Because the DRL agent will provide an integer n where 0 <= n <= maximum_number_of_provinces, n may be larger
     * than the number of CURRENT units. Therefore, we clip it, so that any n greater than our number of SCs maps to
     * the maximum index possible.
     * @param n
     * @return
     */
    private int clipProvinceIndex(int n) {
        if(n >= this.me.getOwnedSCs().size()) {
            return this.me.getOwnedSCs().size() - 1;
        }
        else {
            return n;
        }
    }

    /**
     * This method clips the given integer, in case it is greater than the size of the list of negotiating powers.
     *
     * Because the DRL agent will provide an integer n where 0 <= n <= number_of_players, n may be larger
     * than the number of CURRENT negotiating powers.
     * Therefore, we clip it, so that any n greater than the number of negotiating powers
     * to the maximum index possible.
     * @param n
     * @return
     */
    private int clipPowerIndex(int n) {
        if(n >= this.orderedNegotiatingPowers.size()) {
            return this.orderedNegotiatingPowers.size() - 1;
        }
        else {
            return n;
        }
    }

    /**
     * Each round, after each power has submitted its orders, this method is called several times:
     * once for each order submitted by any other power.
     *
     * @param arg0 An order submitted by any of the other powers.
     */
    @Override
    public void receivedOrder(Order arg0) {
        // TODO Auto-generated method stub

    }
}
