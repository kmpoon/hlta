package hk.ust.cse.lantern.data;

public class Data {
	
    private String name;

	private InstanceCollection instances = new InstanceCollection();
	
	private VariableCollection variables = new VariableCollection();

	public Data() {

	}

	public Data(VariableCollection variables, InstanceCollection instances) {
		this.variables = variables;
		this.instances = instances;
	}

	public String name() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	/**
	 * @return Returns the variables.
	 */
	public VariableCollection variables() {
		return variables;
	}

    /**
     * @return Returns the instances.
     */
    public InstanceCollection instances() {
        return instances;
    }
}
