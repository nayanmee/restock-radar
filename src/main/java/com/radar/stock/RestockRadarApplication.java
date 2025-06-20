package com.radar.stock;

import com.radar.stock.commands.CheckStockCommand;
import io.dropwizard.core.Application;
import io.dropwizard.core.setup.Bootstrap;
import io.dropwizard.core.setup.Environment;

public class RestockRadarApplication extends Application<RestockRadarConfiguration> {

    public static void main(final String[] args) throws Exception {
        new RestockRadarApplication().run(args);
    }

    @Override
    public String getName() {
        return "RestockRadar";
    }

    @Override
    public void initialize(final Bootstrap<RestockRadarConfiguration> bootstrap) {
        bootstrap.addCommand(new CheckStockCommand());
    }

    @Override
    public void run(final RestockRadarConfiguration configuration,
                    final Environment environment) {
        // TODO: implement application
    }

}
