package GeneticAlgorithm;
import MapObjects.Customer;
import MapObjects.Depot;
import MapObjects.Vehicle;
import Utils.Utils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Population contains n Solutions. n = populationSize 种群规模为n的种群
 */
public class Population {
    private List<Depot> depots; // 物流仓库对象列表
    private List<Individual> individuals = new ArrayList<>();

    private Individual alphaIndividual; // 带路径长度的最佳路径————Best Individual (with best fitness)

    private int generation = 0; // 每次tick()后的增量————Increment after each tick() loop
    private List<Double> generations = new ArrayList<>(); //

    private int populationSize; // 种群规模————Number of Solutions in population
    private double crossOverRate; // 交叉因子
    private double mutationRate; // 突变率
    private int tournamentSize; // 锦标赛？？选择次数吗？
    private int numberOfChildren;  // 子代数量？
    private int numberOfParentsToSave; //需要保存的父代数量？
    private int durationPenaltyRate; // 持续惩罚率？
    private int loadPenaltyRate; // 负荷惩罚率？
    private boolean elitism; // 优秀的精英？
    private int k;

    /**
     * Sets parameters      populaiton构造函数生成初始种群，生成n个随机值，n=种群规模
     * Generates initial population which generates n random Solutions. n = populationSize
     */
    //种群的构造函数—————点 与 遗传算法相关属性
    public Population(List<Depot> depots,
                      int populationSize,
                      double crossOverRate,
                      double mutationRate,
                      int tournamentSize,
                      int numberOfChildren,
                      int numberOfParentsToSave,
                      int durationPenaltyRate,
                      int loadPenaltyRate,
                      boolean elitism,
                      int k) {
        this.depots = depots;
        this.populationSize = populationSize;
        this.crossOverRate = crossOverRate;
        this.mutationRate = mutationRate;
        this.tournamentSize = tournamentSize;
        this.numberOfChildren = numberOfChildren;
        this.numberOfParentsToSave = numberOfParentsToSave;
        this.durationPenaltyRate = durationPenaltyRate;
        this.loadPenaltyRate = loadPenaltyRate;
        this.elitism = elitism;
        this.k = k;
    }

    /**
     * One generation of Population 一代种群的的遗传
     * Loops through one generation of each Individual
     * 1. Selection 选择
     * 2. Crossover 交叉
     * 3. Mutation 变异
     * 4. Calculate fitness 计算适应度
     * 5. Filtering 过滤
     */
    public void tick() {
        if (generation == 0) {
            generateInitialPopulation();
            individuals.sort(Comparator.comparingDouble(Individual::getFitness));
        } else {
            List<Individual> children = new ArrayList<>();
            List<Individual> parentsToRemove = new ArrayList<>();
            for (int i = 0; i < numberOfChildren; i++) { // Would this actually make 2*numberOfChildren?
                Individual[] parents = selection();

                // Parents get to crossover if random is less than crossOverRate
                double random = Utils.randomDouble();
                if (random < crossOverRate) {
                    children.addAll(crossOver(parents));
                }

                if (!elitism && children.size() != 0) {

                    parentsToRemove.add(parents[0]);
                    parentsToRemove.add(parents[1]);

                }
            }

            List<Individual> childrenToAdd = new ArrayList<>();
            for (Individual child : children) {
                double random = Utils.randomDouble();
                if (random < mutationRate) {
                    // TODO: Optimize parameters
                    random = Utils.randomDouble();

                    Individual mutatedChild;

                    if (random <= 0.33) {
                        mutatedChild = new Individual(depots, durationPenaltyRate, loadPenaltyRate, child.swapMutation());
                    } else if (random <= 0.66) {
                        mutatedChild = new Individual(depots, durationPenaltyRate, loadPenaltyRate, child.swapMutation2());
                    } else {
                        mutatedChild = new Individual(depots, durationPenaltyRate, loadPenaltyRate, child.crossMutation());
                    }
                    childrenToAdd.add(mutatedChild);
                } else {
                    childrenToAdd.add(child);
                }
            }

            if (!elitism) {
                individuals.removeAll(parentsToRemove);
            }

            individuals.sort(Comparator.comparingDouble(Individual::getFitness)); // Sort by fitness
            List<Individual> parentsToSave = new ArrayList<>(individuals.subList(0, numberOfParentsToSave));
            individuals = childrenToAdd;
            individuals.addAll(parentsToSave);
            individuals.sort(Comparator.comparingDouble(Individual::getFitness)); // Sort by fitness
            individuals = individuals.stream().limit(populationSize).collect(Collectors.toList()); // Cut population to population size
        }
        alphaIndividual = individuals.get(0);
        generations.add(getAlphaFitness());
        generation++;
    }

    /**
     * Generates initial population which generates n random Solutions. n = populationSize
     */
    private void generateInitialPopulation() {//产生初始化种群
        int triesLeft = 1000;
        boolean force = false;

        while (individuals.size() != populationSize) {
            if (triesLeft == 0) {
                force = true;
            }

            Individual individual = new Individual(depots, durationPenaltyRate, loadPenaltyRate);

            // TODO: Parameter optimize
//            boolean successful = individual.generateOptimizedIndividual(force);
            boolean successful = individual.generateOptimizedIndividual2(force);
//            boolean successful = individual.generateRandomIndividual();

            if (successful) {
                individual.calculateFitness();
                individuals.add(individual);
            } else {
                triesLeft--;
            }
        }

        if (triesLeft == 0) {
//            throw new Error("Generating initial population failed - created " + individuals.size() + " of " + populationSize + " individuals");
            System.out.println("Generated population with constraint break");
        }
    }

    private List<Individual> crossOver(Individual[] parents) { // 交叉函数
        List<Vehicle> solutionVehicles = parents[0].getVehicles();
        int randIndex = Utils.randomIndex(solutionVehicles.size());
        Vehicle solutionVehicle = solutionVehicles.get(randIndex);

        List<Vehicle> partnerVehicles = parents[1].getVehicles();
        int randIndex2 = Utils.randomIndex(parents[1].getVehicles().size());

        Vehicle partnerVehicle = partnerVehicles.get(randIndex2);
        ArrayList<ArrayList<Customer>> partsFromS1 = Utils.splitRoute(solutionVehicle.getRoute(), k);
        ArrayList<ArrayList<Customer>> partsFromS2 = Utils.splitRoute(partnerVehicle.getRoute(), k);

        List<Individual> children = new ArrayList<>();

        for (int i = 0; i < k; i++) {
            List<Vehicle> newVehicles = parents[0].singlePointCrossOver(partsFromS2.get(i));
            List<Vehicle> newVehicles2 = parents[1].singlePointCrossOver(partsFromS1.get(i));
            children.add(new Individual(depots, durationPenaltyRate, loadPenaltyRate, newVehicles));
            children.add(new Individual(depots, durationPenaltyRate, loadPenaltyRate, newVehicles2));
        }

        return children;
    }

    private Individual[] selection() {// 选择函数——此处选择轮盘赌方式
        // TODO: Optimize parameters
        //Individual parent1 = tournament();//方式1随机竞争
        Individual parent1 = rouletteWheel();//方式2轮盘赌
        Individual parent2 = parent1;

        while (parent1 == parent2) {
//          parent2 = tournament();
            parent2 = rouletteWheel();
        }

        return new Individual[]{parent1, parent2};
    }

    private Individual rouletteWheel() { // 轮盘赌选择函数
        double totalFitness = getTotalFitness();
        int threshold = Utils.randomIndex((int) totalFitness);
        totalFitness = 0.0;
        for (Individual individual : individuals) {
            totalFitness += individual.getFitness();
            if ((int) totalFitness > threshold) {
                return individual;
            }
        }
        return null;
    }

    private Individual tournament() { // 染色体的随机竞争选择函数
        List<Individual> tournamentMembers = new ArrayList<>();
        for (int i = 0; i < tournamentSize; i++) {
            boolean contained = true;
            Individual member = null;
            while (contained) {
                int randomIndex = Utils.randomIndex(individuals.size());
                member = individuals.get(randomIndex);
                contained = tournamentMembers.contains(member);
            }
            tournamentMembers.add(member);
        }
        tournamentMembers.sort(Comparator.comparingDouble(Individual::getFitness));
        return tournamentMembers.get(0);
    }

    private double getTotalFitness() { // 获取该种群总评价值
        double totalFitness = 0.0;

        for (Individual individual : individuals) {
            totalFitness += individual.getFitness();
        }

        return totalFitness;
    }

    public double getAlphaDuration() { // 获取个体的持续时间
        return alphaIndividual.getDuration();
    }

    public double getAlphaFitness() { // 获取个体的评价值
        return alphaIndividual.getFitness();
    }

    public Individual getAlphaIndividual() { // 获取最佳染色体
        return alphaIndividual;
    }

    public double getAverageFitness() { // 获取种群平均评价值
        double totalFitness = getTotalFitness();
        return totalFitness / individuals.size();
    }

    public int getGeneration() { // 获取代数
        return generation;
    }

    public boolean isAlphaValid() { // 判断该个体是否有效
        return alphaIndividual.isValid();
    }

    public List<Double> getGenerations() { // 获取代数列表？？
        return generations;
    }
}
