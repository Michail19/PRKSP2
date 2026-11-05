// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

// Remix IDE: Solidity Unit Testing plugin предоставляет Assert.
import "remix_tests.sol";
import "../contracts/DeliveryLifecycle.sol";
import "../contracts/DeliveryActor.sol";

contract DeliveryLifecycleTest {
    DeliveryLifecycle private target;
    DeliveryActor private customer;
    DeliveryActor private courier;
    DeliveryActor private stranger;

    // Каждый тест получает свежий контракт и отдельные роли.
    function beforeEach() public {
        target = new DeliveryLifecycle();
        customer = new DeliveryActor();
        courier = new DeliveryActor();
        stranger = new DeliveryActor();
    }

    function testCreateAndRead() public {
        uint256 id = customer.create(target, 1, 3, 4039928);
        DeliveryLifecycle.Delivery memory value = target.getDelivery(id);

        Assert.equal(id, uint256(1), "First delivery ID must be 1");
        Assert.equal(value.orderId, uint256(1), "Wrong orderId");
        Assert.equal(value.darkstoreId, uint256(3), "Wrong darkstoreId");
        Assert.equal(value.priceKopecks, uint256(4039928), "Wrong price");
        Assert.equal(value.customer, address(customer), "Wrong customer");
        Assert.equal(uint256(value.status), uint256(0), "Status must be Created");
    }

    function testHappyPath() public {
        uint256 id = customer.create(target, 10, 3, 10000);
        target.assignCourier(id, address(courier)); // test contract is operator
        courier.accept(target, id);
        courier.start(target, id);
        courier.complete(target, id);

        Assert.equal(
            uint256(target.getStatus(id)),
            uint256(DeliveryLifecycle.Status.Delivered),
            "Status must be Delivered"
        );
    }

    function testCannotCompleteDirectlyFromCreated() public {
        uint256 id = customer.create(target, 11, 3, 10000);
        target.assignCourier(id, address(courier));

        try courier.complete(target, id) {
            Assert.ok(false, "Created -> Delivered must revert");
        } catch {
            Assert.ok(true, "Direct completion rejected");
        }
    }

    function testCannotStartBeforeAcceptance() public {
        uint256 id = customer.create(target, 12, 3, 10000);
        target.assignCourier(id, address(courier));

        try courier.start(target, id) {
            Assert.ok(false, "Created -> InTransit must revert");
        } catch {
            Assert.ok(true, "Skipping Accepted rejected");
        }
    }

    function testOnlyOperatorCanAssign() public {
        uint256 id = customer.create(target, 13, 3, 10000);

        try stranger.assign(target, id, address(courier)) {
            Assert.ok(false, "Unauthorized assignment must revert");
        } catch {
            Assert.ok(true, "Non-operator rejected");
        }
    }

    function testOnlyAssignedCourierCanStart() public {
        uint256 id = customer.create(target, 14, 3, 10000);
        target.assignCourier(id, address(courier));
        courier.accept(target, id);

        try stranger.start(target, id) {
            Assert.ok(false, "Wrong courier must not start");
        } catch {
            Assert.ok(true, "Wrong courier rejected");
        }
    }

    function testCustomerCanCancelBeforeTransit() public {
        uint256 id = customer.create(target, 15, 3, 10000);
        target.assignCourier(id, address(courier));
        courier.accept(target, id);
        customer.cancel(target, id);

        Assert.equal(
            uint256(target.getStatus(id)),
            uint256(DeliveryLifecycle.Status.Cancelled),
            "Status must be Cancelled"
        );
    }

    function testCannotCancelAfterTransit() public {
        uint256 id = customer.create(target, 16, 3, 10000);
        target.assignCourier(id, address(courier));
        courier.accept(target, id);
        courier.start(target, id);

        try customer.cancel(target, id) {
            Assert.ok(false, "Cancellation after InTransit must revert");
        } catch {
            Assert.ok(true, "Late cancellation rejected");
        }
    }

    function testOtherUserCannotCancel() public {
        uint256 id = customer.create(target, 17, 3, 10000);

        try stranger.cancel(target, id) {
            Assert.ok(false, "Only customer may cancel");
        } catch {
            Assert.ok(true, "Unauthorized cancellation rejected");
        }
    }

    function testDuplicateOrderRejected() public {
        customer.create(target, 18, 3, 10000);

        try customer.create(target, 18, 3, 10000) returns (uint256 secondId) {
            Assert.ok(false, "Duplicate orderId must revert");
        } catch {
            Assert.ok(true, "Duplicate order rejected");
        }
    }

    function testMissingDeliveryRejected() public {
        try target.getStatus(999) returns (DeliveryLifecycle.Status missingStatus) {
            Assert.ok(false, "Missing delivery must revert");
        } catch {
            Assert.ok(true, "Missing delivery rejected");
        }
    }

    function testNoRepeatedCompletion() public {
        uint256 id = customer.create(target, 19, 3, 10000);
        target.assignCourier(id, address(courier));
        courier.accept(target, id);
        courier.start(target, id);
        courier.complete(target, id);

        try courier.complete(target, id) {
            Assert.ok(false, "Cannot complete twice");
        } catch {
            Assert.ok(true, "Repeated completion rejected");
        }
    }
}
