// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

/**
 * @title DeliveryLifecycle
 * @notice Учебный смарт-контракт жизненного цикла доставки для АС сервиса доставки.
 * @dev Не принимает деньги: priceKopecks — только информационная сумма заказа.
 *      Персональные данные, адреса доставки и сведения о товарах остаются вне блокчейна.
 */
contract DeliveryLifecycle {
    enum Status {
        Created,    // 0 — доставка создана
        Accepted,   // 1 — назначенный курьер принял доставку
        InTransit,  // 2 — курьер начал доставку
        Delivered,  // 3 — доставка завершена
        Cancelled   // 4 — доставка отменена
    }

    struct Delivery {
        uint256 deliveryId;
        uint256 orderId;
        uint256 darkstoreId;
        address customer;
        address courier;
        uint256 priceKopecks;
        Status status;
        uint256 createdAt;
    }

    // Оператор — аккаунт, который развернул контракт в Remix.
    address public immutable operator;
    uint256 public nextDeliveryId = 1;

    mapping(uint256 => Delivery) private deliveries;
    mapping(uint256 => bool) public orderHasDelivery;

    event DeliveryCreated(
        uint256 indexed deliveryId,
        uint256 indexed orderId,
        address indexed customer,
        uint256 darkstoreId,
        uint256 priceKopecks
    );

    event CourierAssigned(
        uint256 indexed deliveryId,
        address indexed courier
    );

    event DeliveryStatusChanged(
        uint256 indexed deliveryId,
        Status previousStatus,
        Status newStatus,
        address indexed changedBy
    );

    error OnlyOperator(address caller);
    error OnlyCustomer(address caller, address expectedCustomer);
    error OnlyAssignedCourier(address caller, address expectedCourier);
    error DeliveryNotFound(uint256 deliveryId);
    error InvalidOrderId();
    error InvalidDarkstoreId();
    error InvalidPrice();
    error DuplicateOrder(uint256 orderId);
    error ZeroCourierAddress();
    error CourierNotAssigned(uint256 deliveryId);
    error CourierAssignmentClosed(Status currentStatus);
    error InvalidStatusTransition(Status currentStatus, Status nextStatus);

    constructor() {
        operator = msg.sender;
    }

    modifier onlyOperator() {
        if (msg.sender != operator) {
            revert OnlyOperator(msg.sender);
        }
        _;
    }

    /**
     * @notice Создание доставки. Отправитель транзакции становится customer.
     * @param orderId Идентификатор заказа из системы доставки.
     * @param darkstoreId Идентификатор даркстора из BootcampLabs.
     * @param priceKopecks Сумма в копейках; ETH не переводится.
     * @return deliveryId Идентификатор созданной доставки.
     */
    function createDelivery(
        uint256 orderId,
        uint256 darkstoreId,
        uint256 priceKopecks
    ) external returns (uint256 deliveryId) {
        if (orderId == 0) revert InvalidOrderId();
        if (darkstoreId == 0) revert InvalidDarkstoreId();
        if (priceKopecks == 0) revert InvalidPrice();
        if (orderHasDelivery[orderId]) revert DuplicateOrder(orderId);

        deliveryId = nextDeliveryId;
        nextDeliveryId += 1;
        orderHasDelivery[orderId] = true;

        deliveries[deliveryId] = Delivery({
            deliveryId: deliveryId,
            orderId: orderId,
            darkstoreId: darkstoreId,
            customer: msg.sender,
            courier: address(0),
            priceKopecks: priceKopecks,
            status: Status.Created,
            createdAt: block.timestamp
        });

        emit DeliveryCreated(
            deliveryId,
            orderId,
            msg.sender,
            darkstoreId,
            priceKopecks
        );
    }

    /**
     * @notice Оператор назначает курьера, пока доставка в статусе Created.
     */
    function assignCourier(
        uint256 deliveryId,
        address courier
    ) external onlyOperator {
        Delivery storage delivery = _getDelivery(deliveryId);

        if (courier == address(0)) revert ZeroCourierAddress();
        if (delivery.status != Status.Created) {
            revert CourierAssignmentClosed(delivery.status);
        }

        delivery.courier = courier;
        emit CourierAssigned(deliveryId, courier);
    }

    /**
     * @notice Назначенный курьер принимает доставку: Created -> Accepted.
     */
    function acceptDelivery(uint256 deliveryId) external {
        Delivery storage delivery = _getDelivery(deliveryId);
        _checkCourier(delivery);
        _changeStatus(delivery, Status.Created, Status.Accepted);
    }

    /**
     * @notice Назначенный курьер начинает доставку: Accepted -> InTransit.
     */
    function startDelivery(uint256 deliveryId) external {
        Delivery storage delivery = _getDelivery(deliveryId);
        _checkCourier(delivery);
        _changeStatus(delivery, Status.Accepted, Status.InTransit);
    }

    /**
     * @notice Назначенный курьер завершает доставку: InTransit -> Delivered.
     */
    function completeDelivery(uint256 deliveryId) external {
        Delivery storage delivery = _getDelivery(deliveryId);
        _checkCourier(delivery);
        _changeStatus(delivery, Status.InTransit, Status.Delivered);
    }

    /**
     * @notice Клиент может отменить доставку до начала движения курьера.
     *         Разрешено: Created/Accepted -> Cancelled.
     */
    function cancelDelivery(uint256 deliveryId) external {
        Delivery storage delivery = _getDelivery(deliveryId);

        if (msg.sender != delivery.customer) {
            revert OnlyCustomer(msg.sender, delivery.customer);
        }
        if (
            delivery.status != Status.Created &&
            delivery.status != Status.Accepted
        ) {
            revert InvalidStatusTransition(delivery.status, Status.Cancelled);
        }

        Status previousStatus = delivery.status;
        delivery.status = Status.Cancelled;
        emit DeliveryStatusChanged(
            deliveryId,
            previousStatus,
            Status.Cancelled,
            msg.sender
        );
    }

    /**
     * @notice Получить сохранённые сведения; ошибка, если доставка отсутствует.
     */
    function getDelivery(uint256 deliveryId)
        external
        view
        returns (Delivery memory)
    {
        Delivery storage delivery = _getDelivery(deliveryId);
        return delivery;
    }

    function getStatus(uint256 deliveryId) external view returns (Status) {
        Delivery storage delivery = _getDelivery(deliveryId);
        return delivery.status;
    }

    function getDeliveryCount() external view returns (uint256) {
        return nextDeliveryId - 1;
    }

    function _getDelivery(uint256 deliveryId)
        private
        view
        returns (Delivery storage delivery)
    {
        delivery = deliveries[deliveryId];
        if (delivery.customer == address(0)) {
            revert DeliveryNotFound(deliveryId);
        }
    }

    function _checkCourier(Delivery storage delivery) private view {
        if (delivery.courier == address(0)) {
            revert CourierNotAssigned(delivery.deliveryId);
        }
        if (msg.sender != delivery.courier) {
            revert OnlyAssignedCourier(msg.sender, delivery.courier);
        }
    }

    function _changeStatus(
        Delivery storage delivery,
        Status expected,
        Status next
    ) private {
        if (delivery.status != expected) {
            revert InvalidStatusTransition(delivery.status, next);
        }

        Status previousStatus = delivery.status;
        delivery.status = next;
        emit DeliveryStatusChanged(
            delivery.deliveryId,
            previousStatus,
            next,
            msg.sender
        );
    }
}
